// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.hierarchy;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree.*;
import com.intellij.psi.stubs.Stub;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubTree;
import com.intellij.psi.stubs.StubTreeBuilder;
import com.intellij.psi.stubsHierarchy.StubHierarchyIndexer;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.FileContentImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.*;
import org.jetbrains.plugins.groovy.lang.psi.stubs.elements.GrStubFileElementType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GrStubIndexer extends StubHierarchyIndexer {
  @Override
  public int getVersion() {
    return GrStubFileElementType.STUB_VERSION + 1;
  }

  @Override
  public boolean handlesFile(@NotNull VirtualFile file) {
    return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE;
  }

  @Nullable
  @Override
  public Unit indexFile(@NotNull FileContent content) {
    if (!isNormalGroovyFile(((FileContentImpl)content).getPsiFileForPsiDependentIndex())) return null;

    Stub stubTree = StubTreeBuilder.buildStubTree(content);
    if (!(stubTree instanceof GrFileStub)) return null;

    GrFileStub grFileStub = (GrFileStub)stubTree;
    new StubTree(grFileStub, false);

    String pid = "";
    ArrayList<ClassDecl> classList = new ArrayList<>();
    Set<String> usedNames = new HashSet<>();
    for (StubElement<?> el : grFileStub.getChildrenStubs()) {
      if (el instanceof GrPackageDefinitionStub) {
        GrPackageDefinitionStub packageStub = (GrPackageDefinitionStub)el;
        String pkgName = packageStub.getPackageName();
        if (pkgName != null) {
          pid = id(pkgName, false, null);
        }
      }

      if (el instanceof GrTypeDefinitionStub) {
        ClassDecl classDecl = processClassDecl((GrTypeDefinitionStub)el, usedNames, false);
        if (classDecl != null) {
          classList.add(classDecl);
        }
      }
    }
    ArrayList<Import> importList = new ArrayList<>();
    for (StubElement<?> el : grFileStub.getChildrenStubs()) {
      if (el instanceof GrImportStatementStub) {
        processImport((GrImportStatementStub) el, importList, usedNames);
      }
    }
    ClassDecl[] classes = classList.isEmpty() ? ClassDecl.EMPTY_ARRAY : classList.toArray(ClassDecl.EMPTY_ARRAY);
    Import[] imports = importList.isEmpty() ? Import.EMPTY_ARRAY : importList.toArray(Import.EMPTY_ARRAY);
    return new Unit(pid, IndexTree.GROOVY, imports, classes);
  }

  private static boolean isNormalGroovyFile(final PsiFile file) {
    return file instanceof GroovyFile && !hasSpecialScriptType((GroovyFile)file);
  }

  private static boolean hasSpecialScriptType(GroovyFile file) {
    return file.isScript() &&
           ContainerUtil.exists(GroovyScriptTypeDetector.EP_NAME.getExtensions(), detector -> detector.isSpecificScriptFile(file));
  }

  @Nullable
  private static Decl processMember(StubElement<?> el, Set<String> namesCache) {
    if (el instanceof GrTypeDefinitionStub) {
      return processClassDecl((GrTypeDefinitionStub)el, namesCache, true);
    }
    ArrayList<Decl> innerList = new ArrayList<>();
    for (StubElement childElement : el.getChildrenStubs()) {
      Decl innerDef = processMember(childElement, namesCache);
      if (innerDef != null) {
        innerList.add(innerDef);
      }
    }
    return innerList.isEmpty() ? null : new MemberDecl(innerList.toArray(Decl.EMPTY_ARRAY));
  }

  @Nullable
  private static ClassDecl processClassDecl(GrTypeDefinitionStub classStub, Set<String> namesCache, boolean inner) {
    ArrayList<String> superList = new ArrayList<>();
    ArrayList<Decl> innerList = new ArrayList<>();
    if (classStub.isAnonymous()) {
      String name = classStub.getBaseClassName();
      if (name != null) {
        superList.add(id(name, true, namesCache));
      }
    }

    for (StubElement el : classStub.getChildrenStubs()) {
      if (el instanceof GrReferenceListStub) {
        GrReferenceListStub refList = (GrReferenceListStub)el;
        if (el.getStubType() == GroovyElementTypes.IMPLEMENTS_CLAUSE || el.getStubType() == GroovyElementTypes.EXTENDS_CLAUSE) {
          for (String extName : refList.getBaseClasses()) {
            superList.add(id(extName, true, namesCache));
          }
        }
      }
      Decl member = processMember(el, namesCache);
      if (member != null) {
        innerList.add(member);
      }
    }
    int flags = translateFlags(classStub);
    if (inner && !superList.isEmpty()) {
      flags |= IndexTree.SUPERS_UNRESOLVED; // 'extends' list resolves to classes from the current package first, and those can be in a language unknown to this hierarchy
    }
    String[] supers = superList.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : ArrayUtil.toStringArray(superList);
    Decl[] inners = innerList.isEmpty() ? Decl.EMPTY_ARRAY : innerList.toArray(Decl.EMPTY_ARRAY);
    return new ClassDecl(classStub.getStubId(), flags, classStub.getName(), supers, inners);
  }

  private static int translateFlags(GrTypeDefinitionStub classStub) {
    int flags = 0;
    flags = BitUtil.set(flags, IndexTree.ENUM, classStub.isEnum());
    flags = BitUtil.set(flags, IndexTree.ANNOTATION, classStub.isAnnotationType());
    return flags;
  }

  private static void processImport(GrImportStatementStub imp, List<? super Import> imports, Set<String> namesCache) {
    String fullName = imp.getFqn();
    if (fullName == null) return;
    if (imp.isOnDemand() || namesCache.contains(shortName(fullName))) {
      imports.add(new Import(fullName, imp.isStatic(), imp.isOnDemand(), imp.getAliasName()));
    }
  }

  private static String id(String s, boolean cacheFirstId, Set<? super String> namesCache) {
    String id = PsiNameHelper.getQualifiedClassName(s, true);
    if (cacheFirstId) {
      int index = id.indexOf('.');
      String firstId = index > 0 ? s.substring(0, index) : id;
      namesCache.add(firstId);
    }
    return id;
  }

  private static String shortName(String s) {
    int dotIndex = s.lastIndexOf('.');
    return dotIndex > 0 ? s.substring(dotIndex + 1) : null;
  }
}


