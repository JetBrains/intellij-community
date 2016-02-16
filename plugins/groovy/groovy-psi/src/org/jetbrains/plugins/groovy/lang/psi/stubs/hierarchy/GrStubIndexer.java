/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs.hierarchy;

import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.stubs.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GrStubIndexer {

  @Nullable
  public static Unit translate(int fileId, GrFileStub grFileStub) {
    if (grFileStub.isScript()) {
      return new Unit(fileId, null, IndexTree.GROOVY, Import.EMPTY_ARRAY, ClassDecl.EMPTY_ARRAY);
    }
    String pid = null;
    ArrayList<ClassDecl> classList = new ArrayList<ClassDecl>();
    Set<String> usedNames = new HashSet<String>();
    for (StubElement<?> el : grFileStub.getChildrenStubs()) {
      if (el instanceof GrPackageDefinitionStub) {
        GrPackageDefinitionStub packageStub = (GrPackageDefinitionStub)el;
        String pkgName = packageStub.getPackageName();
        if (pkgName != null) {
          pid = id(pkgName, false, null);
        }
      }

      if (el instanceof GrTypeDefinitionStub) {
        ClassDecl classDecl = processClassDecl((GrTypeDefinitionStub)el, usedNames);
        if (classDecl != null) {
          classList.add(classDecl);
        }
      }
    }
    ArrayList<Import> importList = new ArrayList<Import>();
    for (StubElement<?> el : grFileStub.getChildrenStubs()) {
      if (el instanceof GrImportStatementStub) {
        processImport((GrImportStatementStub) el, importList, usedNames);
      }
    }
    ClassDecl[] classes = classList.isEmpty() ? ClassDecl.EMPTY_ARRAY : classList.toArray(new ClassDecl[classList.size()]);
    Import[] imports = importList.isEmpty() ? Import.EMPTY_ARRAY : importList.toArray(new Import[importList.size()]);
    return new Unit(fileId, pid, IndexTree.GROOVY, imports, classes);

  }

  @Nullable
  private static Decl processMember(StubElement<?> el, Set<String> namesCache) {
    if (el instanceof GrTypeDefinitionStub) {
      GrTypeDefinitionStub classStub = (GrTypeDefinitionStub)el;
      if (!classStub.isAnonymousInQualifiedNew()) {
        return processClassDecl(classStub, namesCache);
      }
    }
    ArrayList<Decl> innerList = new ArrayList<Decl>();
    for (StubElement childElement : el.getChildrenStubs()) {
      Decl innerDef = processMember(childElement, namesCache);
      if (innerDef != null) {
        innerList.add(innerDef);
      }
    }
    return innerList.isEmpty() ? null : new MemberDecl(innerList.toArray(new Decl[innerList.size()]));
  }

  @Nullable
  private static ClassDecl processClassDecl(GrTypeDefinitionStub classStub, Set<String> namesCache) {
    ArrayList<String> superList = new ArrayList<String>();
    ArrayList<Decl> innerList = new ArrayList<Decl>();
    if (classStub.isAnonymous()) {
      for (String s : classStub.getSuperClassNames()) {
        superList.add(id(s, true, namesCache));
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
    String[] supers = superList.isEmpty() ? ArrayUtil.EMPTY_STRING_ARRAY : ArrayUtil.toStringArray(superList);
    Decl[] inners = innerList.isEmpty() ? Decl.EMPTY_ARRAY : innerList.toArray(new Decl[innerList.size()]);
    return new ClassDecl(classStub.id, flags, classStub.getName(), supers, inners);
  }

  private static int translateFlags(GrTypeDefinitionStub classStub) {
    int flags = 0;
    if (classStub.isInterface()) {
      flags |= IndexTree.INTERFACE;
    }
    if (classStub.isEnum()) {
      flags |= IndexTree.ENUM;
    }
    if (classStub.isAnnotationType()) {
      flags |= IndexTree.ANNOTATION;
    }
    if (GrStubUtils.isGroovyStaticMemberStub(classStub)) {
      flags |= IndexTree.STATIC;
    }
    return flags;
  }

  private static void processImport(GrImportStatementStub imp, List<Import> imports, Set<String> namesCache) {
    String referenceText = imp.getReferenceText();
    if (referenceText == null) return;
    String fullName = PsiNameHelper.getQualifiedClassName(referenceText, true);
    if (imp.isOnDemand() || namesCache.contains(shortName(fullName))) {
      imports.add(new Import(fullName, imp.isStatic(), imp.isOnDemand(), imp.getAliasName()));
    }
  }

  private static String id(String s, boolean cacheFirstId, Set<String> namesCache) {
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


