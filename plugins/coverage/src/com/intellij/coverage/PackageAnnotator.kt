/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiPackage
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.rt.coverage.data.ClassData
import com.intellij.rt.coverage.data.LineCoverage
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData
import com.intellij.util.containers.HashMap
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes
import org.jetbrains.jps.model.java.JavaSourceRootType

import java.io.File
import java.io.IOException
import java.util.ArrayList
import com.intellij.openapi.module.ModuleUtilCore

/**
 * @author ven
 */
public class PackageAnnotator(private val mySuite: CoverageSuitesBundle,
                              private val myPackage: PsiPackage,
                              private val myConsumer: PackageAnnotator.AnnotationConsumer) {
  private val myProjectData: ProjectData? = mySuite.getCoverageData()
  private val myRootPackageVMName: String = myPackage.getQualifiedName().dotsToSlashes()
  private val myProject: Project = myPackage.getProject()
  private val myManager: PsiManager = PsiManager.getInstance(myProject)
  private val myCoverageManager: CoverageDataManager = CoverageDataManager.getInstance(myProject)

  public trait AnnotationConsumer {
    public fun annotateSourceDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageCoverageInfo, module: Module)

    public fun annotateTestDirectory(virtualFile: VirtualFile, packageCoverageInfo: PackageCoverageInfo, module: Module)

    public fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo)

    public fun annotatePackage(packageQualifiedName: String, packageCoverageInfo: PackageCoverageInfo, flatten: Boolean)

    public fun annotateClass(classQualifiedName: String, classCoverageInfo: ClassCoverageInfo)
  }

  public class ClassCoverageInfo {
    public var totalLineCount: Int = 0
    public var fullyCoveredLineCount: Int = 0
    public var partiallyCoveredLineCount: Int = 0
    public var totalMethodCount: Int = 0
    public var coveredMethodCount: Int = 0

    public var totalClassCount: Int = 0
    public var coveredClassCount: Int = 0
  }

  public open class PackageCoverageInfo {
    public var totalClassCount: Int = 0
    public var coveredClassCount: Int = 0
    public var totalLineCount: Int = 0
    public var coveredLineCount: Int = 0

    public var totalMethodCount: Int = 0
    public var coveredMethodCount: Int = 0

    fun append(other: PackageCoverageInfo) {
      totalClassCount += other.totalClassCount
      coveredClassCount += other.coveredClassCount
      totalLineCount += other.totalLineCount
      coveredLineCount += other.coveredLineCount
      totalMethodCount += other.totalMethodCount
      coveredMethodCount += other.coveredMethodCount
    }

    fun appendClass(classInfo: ClassCoverageInfo) {
      totalClassCount++
      if (classInfo.coveredMethodCount > 0) {
        coveredClassCount++
      }
      totalLineCount += classInfo.totalLineCount
      coveredLineCount += classInfo.fullyCoveredLineCount + classInfo.partiallyCoveredLineCount
      totalMethodCount += classInfo.totalMethodCount
      coveredMethodCount += classInfo.coveredMethodCount
    }
  }

  public class DirCoverageInfo(public val sourceRoot: VirtualFile?) : PackageCoverageInfo()

  fun String.dotsToSlashes() = StringUtil.replaceChar(this, '.', '/')
  fun String.slashesToDots() = StringUtil.replaceChar(this, '/', '.')

  //get read lock myself when needed
  public fun annotate() {
    if (myProjectData == null) return

    if (!isPackageAcceptedByFilters()) return

    val scope = mySuite.getSearchScope(myProject)
    val modules = myCoverageManager.doInReadActionIfProjectOpen {
      ModuleManager.getInstance(myProject).getModules()
    }

    val packageCoverageMap = HashMap<String, PackageCoverageInfo>()
    val flattenPackageCoverageMap = HashMap<String, PackageCoverageInfo>()
    for (module in modules) {
      if (!scope.isSearchInModuleContent(module)) continue
      collectCoverageForRootType(module, flattenPackageCoverageMap, packageCoverageMap, false)
      if (mySuite.isTrackTestFolders()) {
        collectCoverageForRootType(module, flattenPackageCoverageMap, packageCoverageMap, true)
      }
    }

    for ((packageFQName, info) in packageCoverageMap) {
      myConsumer.annotatePackage(packageFQName.slashesToDots(), info)
    }

    for ((packageFQName, info) in flattenPackageCoverageMap) {
      myConsumer.annotatePackage(packageFQName.slashesToDots(), info, true)
    }
  }

  private fun collectCoverageForRootType(module: Module,
                                         flattenPackageCoverageMap: MutableMap<String, PackageCoverageInfo>,
                                         packageCoverageMap: MutableMap<String, PackageCoverageInfo>,
                                         forTests: Boolean) {
    val output = getCompilerOutput(module, forTests)
    if (output != null) {
      val outputRoot = findRelativeFile(myRootPackageVMName, output)
      if (outputRoot.exists()) {
        collectCoverageInformation(outputRoot, packageCoverageMap, flattenPackageCoverageMap, myRootPackageVMName, module, forTests)
      }
    }
  }

  private fun getCompilerOutput(module: Module, forTests: Boolean): VirtualFile? {
    return myCoverageManager.doInReadActionIfProjectOpen {
      val moduleExtension = CompilerModuleExtension.getInstance(module)
      if (forTests) moduleExtension.getCompilerOutputPathForTests() else moduleExtension.getCompilerOutputPath()
    }
  }

  private fun isPackageAcceptedByFilters() =
      mySuite.getSuites().any { it is JavaCoverageSuite && it.isPackageFiltered(myPackage.getQualifiedName()) }

  public fun annotateFilteredClass(psiClass: PsiClass) {
    if (myProjectData == null) return
    val module = ModuleUtilCore.findModuleForPsiElement(psiClass)
    if (module != null) {
      val isInTests = ProjectRootManager.getInstance(module.getProject()).getFileIndex().isInTestSourceContent(psiClass.getContainingFile().getVirtualFile())
      val outputPath = getCompilerOutput(module, isInTests)

      if (outputPath != null) {
        val qualifiedName = psiClass.getQualifiedName()
        if (qualifiedName == null) return
        val packageRoot = findRelativeFile(myRootPackageVMName, outputPath)
        if (packageRoot.exists()) {
          val toplevelClassCoverage = HashMap<String, ClassCoverageInfo>()
          val files = packageRoot.listFiles()
          if (files != null) {
            for (child in files) {
              if (isClassFile(child)) {
                val childName = getClassName(child)
                val classFqVMName = if (myRootPackageVMName.length() > 0) myRootPackageVMName + "/" + childName else childName
                val toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName)
                if (toplevelClassSrcFQName == qualifiedName) {
                  collectClassCoverageInformation(child, PackageCoverageInfo(), toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName)
                }
              }
            }
          }
          for (coverageInfo in toplevelClassCoverage.values()) {
            myConsumer.annotateClass(qualifiedName, coverageInfo)
          }
        }
      }
    }
  }

  private fun collectCoverageInformation(packageOutputRoot: File,
                                         packageCoverageMap: MutableMap<String, PackageCoverageInfo>,
                                         flattenPackageCoverageMap: MutableMap<String, PackageCoverageInfo>,
                                         packageVMName: String,
                                         module: Module,
                                         isTestHierarchy: Boolean): List<DirCoverageInfo>? {
    val dirs = collectCoverageRoots(module, packageVMName, isTestHierarchy)

    val children = packageOutputRoot.listFiles()

    if (children == null) return null

    val toplevelClassCoverage = HashMap<String, ClassCoverageInfo>()
    for (child in children) {
      if (child.isDirectory()) {
        val childName = child.getName()
        val childPackageVMName = if (packageVMName.length() > 0) packageVMName + "/" + childName else childName
        val childCoverageInfo = collectCoverageInformation(child, packageCoverageMap, flattenPackageCoverageMap, childPackageVMName, module, isTestHierarchy)
        if (childCoverageInfo != null) {
          for((coverageInfo, parentDir) in childCoverageInfo.zip(dirs)) {
            parentDir.append(coverageInfo)
          }
        }
      }
      else if (isClassFile(child)) {
        val childName = getClassName(child)
        val classFqVMName = if (packageVMName.length() > 0) packageVMName + "/" + childName else childName
        val classData = myProjectData!!.getClassData(classFqVMName.slashesToDots())
        val source = classData.getSource()
        println(source)


        /*
        val toplevelClassSrcFQName = getSourceToplevelFQName(classFqVMName)
        val containingFile = arrayOfNulls<VirtualFile>(1)
        val isInSource = myCoverageManager.doInReadActionIfProjectOpen<Boolean>(object : Computable<Boolean> {
          override fun compute(): Boolean? {
            val aClass = JavaPsiFacade.getInstance(myManager.getProject()).findClass(toplevelClassSrcFQName, GlobalSearchScope.moduleScope(module))
            if (aClass == null || !aClass.isValid()) return java.lang.Boolean.FALSE
            containingFile[0] = aClass.getContainingFile().getVirtualFile()
            if (containingFile[0] == null) {
              LOG.info("No virtual file found for: " + aClass)
              return null
            }
            val fileIndex = ModuleRootManager.getInstance(module).getFileIndex()
            return fileIndex.isUnderSourceRootOfType(containingFile[0], JavaModuleSourceRootTypes.SOURCES) && (mySuite.isTrackTestFolders() || !fileIndex.isInTestSourceContent(containingFile[0]))
          }
        })
        if (isInSource != null && isInSource.booleanValue()) {
          for (dirCoverageInfo in dirs) {
            if (dirCoverageInfo.sourceRoot != null && VfsUtilCore.isAncestor(dirCoverageInfo.sourceRoot, containingFile[0], false)) {
              collectClassCoverageInformation(child, dirCoverageInfo, toplevelClassCoverage, classFqVMName.replace("/", "."), toplevelClassSrcFQName)
              break
            }
          }
        }
        */
      }
    }

    for ((toplevelClassName, coverageInfo) in toplevelClassCoverage) {
      myConsumer.annotateClass(toplevelClassName, coverageInfo)
    }

    val flattenPackageCoverageInfo = getOrCreateCoverageInfo(flattenPackageCoverageMap, packageVMName)
    for (coverageInfo in toplevelClassCoverage.values()) {
      flattenPackageCoverageInfo.coveredClassCount += coverageInfo.coveredClassCount
      flattenPackageCoverageInfo.totalClassCount += coverageInfo.totalClassCount

      flattenPackageCoverageInfo.coveredLineCount += coverageInfo.fullyCoveredLineCount + coverageInfo.partiallyCoveredLineCount
      flattenPackageCoverageInfo.totalLineCount += coverageInfo.totalLineCount

      flattenPackageCoverageInfo.coveredMethodCount += coverageInfo.coveredMethodCount
      flattenPackageCoverageInfo.totalMethodCount += coverageInfo.totalMethodCount
    }

    val packageCoverageInfo = getOrCreateCoverageInfo(packageCoverageMap, packageVMName)
    for (dir in dirs) {
      packageCoverageInfo.append(dir)

      dir.sourceRoot?.let {
        if (isTestHierarchy) {
          myConsumer.annotateTestDirectory(it, dir, module)
        }
        else {
          myConsumer.annotateSourceDirectory(it, dir, module)
        }
      }
    }

    return dirs
  }

  private fun collectCoverageRoots(module: Module,
                                   packageVMName: String,
                                   isTestHierarchy: Boolean): ArrayList<DirCoverageInfo> {
    val dirs = ArrayList<DirCoverageInfo>()
    val contentEntries = ModuleRootManager.getInstance(module).getContentEntries()
    for (contentEntry in contentEntries) {
      for (folder in contentEntry.getSourceFolders(if (isTestHierarchy) JavaSourceRootType.TEST_SOURCE else JavaSourceRootType.SOURCE)) {
        val file = folder.getFile()
        if (file == null) continue
        val prefix = folder.getPackagePrefix().replaceAll("\\.", "/")
        val relativeSrcRoot = file.findFileByRelativePath(StringUtil.trimStart(packageVMName, prefix))
        dirs.add(DirCoverageInfo(relativeSrcRoot))
      }
    }
    return dirs
  }

  private fun collectClassCoverageInformation(classFile: File,
                                              packageCoverageInfo: PackageCoverageInfo,
                                              toplevelClassCoverage: MutableMap<String, ClassCoverageInfo>,
                                              className: String,
                                              toplevelClassSrcFQName: String) {
    val toplevelClassCoverageInfo = ClassCoverageInfo()

    val classData = myProjectData!!.getClassData(className)

    if (classData != null && classData.getLines() != null) {
      val lines = classData.getLines()
      for (l in lines) {
        if (l is LineData) {
          if (l.getStatus() == LineCoverage.FULL.toInt()) {
            toplevelClassCoverageInfo.fullyCoveredLineCount++
          }
          else if (l.getStatus() == LineCoverage.PARTIAL.toInt()) {
            toplevelClassCoverageInfo.partiallyCoveredLineCount++
          }
          toplevelClassCoverageInfo.totalLineCount++
        }
      }
      val methodSigs = classData.getMethodSigs()
      for (nameAndSig in methodSigs) {
        val covered = classData.getStatus(nameAndSig as String)
        if (covered != LineCoverage.NONE.toInt()) {
          toplevelClassCoverageInfo.coveredMethodCount++
        }
      }
      if (!methodSigs.isEmpty()) {
        toplevelClassCoverageInfo.totalMethodCount += methodSigs.size()
        packageCoverageInfo.appendClass(toplevelClassCoverageInfo)
      }
      else {
        return
      }
    }
    else {
      if (!collectNonCoveredClassInfo(classFile, toplevelClassCoverageInfo, packageCoverageInfo)) return
    }

    val classCoverageInfo = getOrCreateClassCoverageInfo(toplevelClassCoverage, toplevelClassSrcFQName)
    classCoverageInfo.totalLineCount += toplevelClassCoverageInfo.totalLineCount
    classCoverageInfo.fullyCoveredLineCount += toplevelClassCoverageInfo.fullyCoveredLineCount
    classCoverageInfo.partiallyCoveredLineCount += toplevelClassCoverageInfo.partiallyCoveredLineCount

    classCoverageInfo.totalMethodCount += toplevelClassCoverageInfo.totalMethodCount
    classCoverageInfo.coveredMethodCount += toplevelClassCoverageInfo.coveredMethodCount
    if (toplevelClassCoverageInfo.coveredMethodCount > 0) {
      classCoverageInfo.coveredClassCount++
    }
  }


  /*
    return true if there is executable code in the class
   */
  private fun collectNonCoveredClassInfo(classFile: File,
                                         classCoverageInfo: ClassCoverageInfo,
                                         packageCoverageInfo: PackageCoverageInfo): Boolean {
    val content = myCoverageManager.doInReadActionIfProjectOpen {
      try {
        FileUtil.loadFileBytes(classFile)
      } catch(e: IOException) {
        null
      }
    }
    return SourceLineCounterUtil.collectNonCoveredClassInfo(classCoverageInfo, packageCoverageInfo, content, mySuite.isTracingEnabled())
  }

  class object {

    private val LOG = Logger.getInstance("#" + javaClass<PackageAnnotator>().getName())

    private fun findRelativeFile(rootPackageVMName: String, output: VirtualFile): File {
      val outputRoot = VfsUtilCore.virtualToIoFile(output)
      return if (rootPackageVMName.length() > 0) File(outputRoot, FileUtil.toSystemDependentName(rootPackageVMName)) else outputRoot
    }

    private fun isClassFile(classFile: File) = classFile.getName().endsWith(".class")

    private fun getClassName(classFile: File) = StringUtil.trimEnd(classFile.getName(), ".class")

    private fun getOrCreateCoverageInfo(packageCoverageMap: MutableMap<String, PackageCoverageInfo>, packageVMName: String) =
      packageCoverageMap.getOrPut(packageVMName) { PackageCoverageInfo() }

    private fun getOrCreateClassCoverageInfo(toplevelClassCoverage: MutableMap<String, ClassCoverageInfo>,
                                             sourceToplevelFQName: String): ClassCoverageInfo {
      val toplevelClassCoverageInfo = toplevelClassCoverage.getOrPut(sourceToplevelFQName) { ClassCoverageInfo() }
      toplevelClassCoverageInfo.totalClassCount++
      return toplevelClassCoverageInfo
    }

    private fun getSourceToplevelFQName(classFQVMName: String): String {
      var classFQVMName = classFQVMName
      val index = classFQVMName.indexOf('$')
      if (index > 0) classFQVMName = classFQVMName.substring(0, index)
      if (classFQVMName.startsWith("/")) classFQVMName = classFQVMName.substring(1)
      return classFQVMName.replaceAll("/", ".")
    }
  }
}
