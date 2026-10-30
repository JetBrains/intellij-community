// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.debugger

import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerEx
import com.intellij.debugger.engine.evaluation.EvaluationContext
import com.intellij.debugger.engine.jdi.VirtualMachineProxy
import com.intellij.debugger.impl.DebuggerUtilsAsync
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.jdi.StackFrameProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.module.impl.scopes.ModuleWithDependenciesScope
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiClassUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ThreeState
import com.intellij.xdebugger.frame.XStackFrame
import com.sun.jdi.AbsentInformationException
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrSwitchElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSwitchExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.GrTopStatement
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.stubs.GroovyShortNamesCache

open class GroovyPositionManager(private val myDebugProcess: DebugProcess) : PositionManagerEx() {
  open fun getDebugProcess(): DebugProcess = myDebugProcess

  @get:JvmName("getDebugProcessForKotlin")
  @get:JvmSynthetic
  val debugProcess: DebugProcess
    get() = getDebugProcess()

  @Throws(NoDataException::class)
  override fun locationsOfLine(type: ReferenceType, position: SourcePosition): List<Location> {
    checkGroovyFile(position)
    try {
      if (LOG.isDebugEnabled()) {
        LOG.debug("locationsOfLine: $type; $position")
      }
      val line = position.getLine() + 1
      val locations = DebuggerUtilsAsync.locationsOfLineSync(type, DebugProcess.JAVA_STRATUM, null, line)
      if (locations == null || locations.isEmpty()) throw NoDataException.INSTANCE
      return locations
    }
    catch (_: AbsentInformationException) {
      throw NoDataException.INSTANCE
    }
  }

  override fun evaluateCondition(
    context: EvaluationContext,
    frame: StackFrameProxyImpl,
    location: Location,
    expression: String,
  ): ThreeState {
    return ThreeState.UNSURE
  }

  override fun createStackFrame(descriptor: StackFrameDescriptorImpl): XStackFrame? {
    if (isInGroovyFile(descriptor.location) != ThreeState.YES) {
      return null
    }
    return GroovyStackFrame(descriptor, true)
  }

  @Throws(NoDataException::class)
  override fun createPrepareRequest(requestor: ClassPrepareRequestor, position: SourcePosition): ClassPrepareRequest? {
    if (LOG.isDebugEnabled()) {
      LOG.debug("createPrepareRequest: $position")
    }
    checkGroovyFile(position)

    var qName: String? = getOuterClassName(position)
    if (qName != null) {
      return myDebugProcess.getRequestsManager().createClassPrepareRequest(requestor, qName)
    }

    qName = findEnclosingName(position)

    if (qName == null) throw NoDataException.INSTANCE
    val waitRequestor = ClassPrepareRequestor { debuggerProcess, referenceType ->
      val positionManager = (debuggerProcess as DebugProcessImpl).positionManager
      if (!positionManager.locationsOfLine(referenceType, position).isEmpty()) {
        requestor.processClassPrepare(debuggerProcess, referenceType)
      }
    }
    return myDebugProcess.getRequestsManager().createClassPrepareRequest(waitRequestor, "$qName$*")
  }

  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition? {
    if (location == null) throw NoDataException.INSTANCE
    if (isInGroovyFile(location) == ThreeState.NO) throw NoDataException.INSTANCE

    val lineNumber = calcLineIndex(location)
    if (lineNumber < 0) throw NoDataException.INSTANCE

    if (LOG.isDebugEnabled()) {
      LOG.debug("getSourcePosition: $location")
    }
    val psiFile = getPsiFileByLocation(debugProcess.project, location)
    if (psiFile == null) throw NoDataException.INSTANCE

    return SourcePosition.createFromLine(psiFile, lineNumber)
  }

  private fun calcLineIndex(location: Location?): Int {
    if (location == null) return -1
    return DebuggerUtilsEx.getLineNumber(location, true)
  }

  private fun getPsiFileByLocation(project: Project, location: Location?): PsiFile? {
    if (location == null) return null

    val refType = location.declaringType()
    if (refType == null) return null

    val originalQName = refType.name().replace('/', '.')
    val dollar = originalQName.indexOf('$')
    val runtimeName = if (dollar >= 0) originalQName.substring(0, dollar) else originalQName
    val qName: String = getOriginalQualifiedName(refType, runtimeName)

    val searchScope = myDebugProcess.getSearchScope()
    val cache = GroovyShortNamesCache.getGroovyShortNamesCache(project)
    try {
      var classes = cache.getClassesByFQName(qName, searchScope, true)
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, searchScope, false)
      }
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, GlobalSearchScope.projectScope(project), false)
      }
      if (classes.isEmpty()) {
        classes = cache.getClassesByFQName(qName, addModuleContent(searchScope), false)
      }
      if (!classes.isEmpty()) {
        classes.sortWith(PsiClassUtil.createScopeComparator(searchScope))
        val clazz: PsiClass? = classes.first()
        if (clazz != null) return clazz.getContainingFile()
      }
    }
    catch (_: IndexNotReadyException) {
      return null
    }

    return getExtraScriptIfNotFound(project, refType, runtimeName, searchScope)
  }

  @Throws(NoDataException::class)
  override fun getAllClasses(position: SourcePosition): List<ReferenceType> {
    if (LOG.isDebugEnabled()) {
      LOG.debug("getAllClasses: $position")
    }

    checkGroovyFile(position)
    val result = ReadAction.computeBlocking<List<ReferenceType>?, RuntimeException> {
      val sourceImage: GroovyPsiElement? = findReferenceTypeSourceImage(position)
      if (sourceImage is GrTypeDefinition && !sourceImage.isAnonymous()) {
        val qName: String? = getClassNameForJvm(sourceImage)
        if (qName != null) return@computeBlocking VirtualMachineProxy.getCurrent().classesByName(qName)
      }
      else if (sourceImage == null) {
        val scriptName: String? = getScriptQualifiedName(position)
        if (scriptName != null) return@computeBlocking VirtualMachineProxy.getCurrent().classesByName(scriptName)
      }
      else {
        val enclosingName: String? = findEnclosingName(position)
        if (enclosingName == null) return@computeBlocking null

        val outers = VirtualMachineProxy.getCurrent().classesByName(enclosingName)
        val result1: MutableList<ReferenceType> = ArrayList(outers.size)
        for (outer in outers) {
          val nested = findNested(outer, sourceImage, position)
          if (nested != null) {
            result1.add(nested)
          }
        }
        return@computeBlocking result1
      }
      null
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("getAllClasses = $result")
    }
    if (result == null) throw NoDataException.INSTANCE
    return result
  }

  private fun findNested(fromClass: ReferenceType, toFind: GroovyPsiElement, classPosition: SourcePosition): ReferenceType? {
    val vmProxy = VirtualMachineProxy.getCurrent()
    if (fromClass.isPrepared) {
      val nestedTypes = vmProxy.nestedTypes(fromClass)

      for (nested in nestedTypes) {
        val found = findNested(nested, toFind, classPosition)
        if (found != null) {
          return found
        }
      }

      try {
        val lineNumber = classPosition.getLine() + 1
        if (!DebuggerUtilsAsync.locationsOfLineSync(fromClass, lineNumber).isEmpty()) {
          return fromClass
        }
        for (location in DebuggerUtilsAsync.allLineLocationsSync(fromClass)) {
          val candidateFirstPosition = SourcePosition.createFromLine(
            toFind.getContainingFile(), location.lineNumber() - 1
          )
          if (toFind == findReferenceTypeSourceImage(candidateFirstPosition)) {
            return fromClass
          }
          break // isApplicable only the first location
        }
      }
      catch (_: AbsentInformationException) {
      }
    }
    return null
  }

  override fun isAcceptedFileType(fileType: FileType): Boolean {
    return fileType === GroovyFileType.GROOVY_FILE_TYPE ||
           ScriptPositionManagerHelper.EP_NAME.findFirstSafe { it.acceptedFileTypes.contains(fileType) } != null
  }

  companion object {
    private val LOG = Logger.getInstance(GroovyPositionManager::class.java)

    private fun isInGroovyFile(location: Location?): ThreeState {
      if (location != null) {
        val refType = location.declaringType()
        try {
          val safeName = refType.sourceName()
          val fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(safeName)
          if (fileType === UnknownFileType.INSTANCE) {
            return ThreeState.UNSURE
          }
          if (fileType is LanguageFileType) {
            if (fileType.language === GroovyLanguage) {
              return ThreeState.YES
            }
          }
        }
        catch (_: AbsentInformationException) {
        }
      }
      return ThreeState.NO
    }

    private fun findReferenceTypeSourceImage(position: SourcePosition): GroovyPsiElement? {
      val file = position.getFile()
      if (file !is GroovyFileBase) return null
      val element = file.findElementAt(position.getOffset())
      if (element == null) return null
      return getEnclosingPsiForElement(element)
    }

    /**
     * In Groovy, there are some transformations performed before compiling or interpreting the source code.
     *
     * First, all closures and lambdas are transformed to a local class extending `groovy.lang.Closure`. Therefore, code inside closures
     * does not correspond to the class of the methods where the closure is defined.
     *
     * Second, there is no such thing as a "switch expression".
     * The following
     * ```text
     * switch(10) {
     * case 20 -> 30
     * case 30 -> {
     * 40
     * }
     * }
     * ```
     * is transformed to
     * ```text
     * { ->
     * switch(10) {
     * case 20: return 30
     * case 30: return {->
     * return 40
     * }()
     * }
     * }()
     * ```
     * So the code inside switch expressions corresponds to a separate local class too.
     */
    private fun getEnclosingPsiForElement(element: PsiElement): GroovyPsiElement? {
      var element = element
      while (true) {
        val parent: GroovyPsiElement? = PsiTreeUtil.getParentOfType<GrTopStatement?>(
          element,
          GrSwitchExpression::class.java,
          GrFunctionalExpression::class.java,
          GrTypeDefinition::class.java
        )
        if (!(parent is GrSwitchElement && PsiUtil.isPlainSwitchStatement(parent))) {
          return parent
        }
        else {
          element = parent
        }
      }
    }

    private fun findEnclosingTypeDefinition(position: SourcePosition): PsiClass? {
      val file = position.getFile()
      if (file !is GroovyFileBase) return null
      var element = file.findElementAt(position.getOffset())
      while (true) {
        element = PsiTreeUtil.getParentOfType(element, GrTypeDefinition::class.java, GroovyFileBase::class.java)
        if (element is GroovyFileBase) {
          return element.getScriptClass()
        }
        else if (element is GrTypeDefinition && !element.isAnonymous()) {
          return element
        }
      }
    }

    @Throws(NoDataException::class)
    private fun checkGroovyFile(position: SourcePosition) {
      if (position.getFile() !is GroovyFileBase) {
        throw NoDataException.INSTANCE
      }
    }

    private fun findEnclosingName(position: SourcePosition): String? {
      return ReadAction.compute<String?, RuntimeException> {
        val typeDefinition: PsiClass? = findEnclosingTypeDefinition(position)
        if (typeDefinition != null) {
          return@compute getClassNameForJvm(typeDefinition)
        }
        getScriptQualifiedName(position)
      }
    }

    private fun getOuterClassName(position: SourcePosition): String? {
      return ReadAction.compute<String?, RuntimeException> {
        val sourceImage: GroovyPsiElement? = findReferenceTypeSourceImage(position)
        if (sourceImage is GrTypeDefinition) {
          return@compute getClassNameForJvm(sourceImage)
        }
        else if (sourceImage == null) {
          return@compute getScriptQualifiedName(position)
        }
        null
      }
    }

    private fun getClassNameForJvm(typeDefinition: PsiClass): String? {
      val suffix = if (typeDefinition is GrTypeDefinition && typeDefinition.isTrait()) $$"$Trait$Helper" else ""
      val psiClass = typeDefinition.getContainingClass()
      if (psiClass != null) {
        val parent: String? = getClassNameForJvm(psiClass)
        return if (parent == null) null else parent + "$" + typeDefinition.getName() + suffix
      }

      val file = typeDefinition.getContainingFile()
      if (file is GroovyFile && file.isScript()) {
        for (helper in ScriptPositionManagerHelper.EP_NAME.extensions) {
          val s = if (helper.isAppropriateScriptFile(file)) helper.customizeClassName(typeDefinition) else null
          if (s != null) {
            return s
          }
        }
      }

      val qname = typeDefinition.getQualifiedName()
      return if (qname == null) null else qname + suffix
    }

    private fun getScriptQualifiedName(position: SourcePosition): String? {
      val file = position.getFile()
      if (file is GroovyFile) {
        return getScriptFQName(file)
      }
      return null
    }

    private fun getExtraScriptIfNotFound(
      project: Project,
      refType: ReferenceType,
      runtimeName: String,
      searchScope: GlobalSearchScope,
    ): PsiFile? {
      for (helper in ScriptPositionManagerHelper.EP_NAME.extensions) {
        if (helper.isAppropriateRuntimeName(runtimeName)) {
          val file = helper.getExtraScriptIfNotFound(refType, runtimeName, project, searchScope)
          if (file != null) return file
        }
      }
      return null
    }

    private fun addModuleContent(scope: GlobalSearchScope): GlobalSearchScope {
      if (scope is ModuleWithDependenciesScope) {
        val module = scope.module
        if (!module.isDisposed()) {
          return scope.uniteWith(module.getModuleContentWithDependenciesScope())
        }
      }
      return scope
    }

    private fun getOriginalQualifiedName(refType: ReferenceType, runtimeName: String): String {
      for (helper in ScriptPositionManagerHelper.EP_NAME.extensions) {
        if (helper.isAppropriateRuntimeName(runtimeName)) {
          val originalScriptName = helper.getOriginalScriptName(refType, runtimeName)
          if (originalScriptName != null) return originalScriptName
        }
      }
      return runtimeName
    }

    private fun getScriptFQName(groovyFile: GroovyFile): String {
      val packageName = groovyFile.getPackageName()
      val fileName: String = getRuntimeScriptName(groovyFile)
      return StringUtil.getQualifiedName(packageName, fileName)
    }

    private fun getRuntimeScriptName(groovyFile: GroovyFile): String {
      if (groovyFile.isScript()) {
        for (helper in ScriptPositionManagerHelper.EP_NAME.extensions) {
          if (helper.isAppropriateScriptFile(groovyFile)) {
            val runtimeScriptName = helper.getRuntimeScriptName(groovyFile)
            if (runtimeScriptName != null) return runtimeScriptName
          }
        }
      }
      val vFile = checkNotNull(groovyFile.getVirtualFile())
      return vFile.nameWithoutExtension
    }
  }
}