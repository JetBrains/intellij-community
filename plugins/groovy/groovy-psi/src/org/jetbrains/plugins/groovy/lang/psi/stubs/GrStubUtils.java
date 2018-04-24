// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.io.DataInputOutputUtilRt.readSeq;
import static com.intellij.openapi.util.io.DataInputOutputUtilRt.writeSeq;
import static org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListUtil.hasMaskModifier;

public class GrStubUtils {

  public static void writeStringArray(@NotNull StubOutputStream dataStream, @NotNull String[] array) throws IOException {
    writeSeq(dataStream, ContainerUtil.newArrayList(array), dataStream::writeName);
  }

  @NotNull
  public static String[] readStringArray(@NotNull StubInputStream dataStream) throws IOException {
    return ArrayUtil.toStringArray(readSeq(dataStream, dataStream::readNameString));
  }

  public static void writeNullableString(StubOutputStream dataStream, @Nullable String typeText) throws IOException {
    DataInputOutputUtil.writeNullable(dataStream, typeText, dataStream::writeUTFFast);
  }

  @Nullable
  public static String readNullableString(StubInputStream dataStream) throws IOException {
    return DataInputOutputUtil.readNullable(dataStream, dataStream::readUTFFast);
  }

  @Nullable
  public static String getTypeText(@Nullable GrTypeElement typeElement) {
    return typeElement == null ? null : typeElement.getText();
  }

  @NotNull
  private static Map<String, String> getAliasMapping(@Nullable PsiFile file) {
    if (!(file instanceof GroovyFile)) return Collections.emptyMap();
    return CachedValuesManager.getCachedValue(file, () -> {
      Map<String, String> mapping = ContainerUtil.newHashMap();
      for (GrImportStatement importStatement : ((GroovyFile)file).getImportStatements()) {
        if (importStatement.getImportReference() != null && !importStatement.isStatic() && importStatement.isAliasedImport()) {
          String importName = importStatement.getImportReference().getClassNameText();
          String importedName = importStatement.getImportedName();
          if (importedName != null) {
            mapping.put(importedName, importName);
          }
        }
      }
      return CachedValueProvider.Result.create(mapping, file);
    });
  }

  @Nullable
  public static String getReferenceName(@NotNull GrReferenceElement element) {
    final String referenceName = element.getReferenceName();
    if (referenceName == null) return null;

    // Foo -> java.util.List
    final String mappedFqn = getAliasMapping(element.getContainingFile()).get(referenceName);
    final String fullText = element.getText();

    // alias: Foo<String> -> java.util.List<String>
    // unqualified ref: List<String> -> List<String>
    // qualified ref: java.util.List<String> -> java.util.List<String>
    return mappedFqn == null || element.isQualified() ? fullText : fullText.replace(referenceName, mappedFqn);
  }

  @Nullable
  public static String getBaseClassName(@NotNull GrTypeDefinition psi) {
    if (!(psi instanceof GrAnonymousClassDefinition)) return null;
    return getReferenceName(((GrAnonymousClassDefinition)psi).getBaseClassReferenceGroovy());
  }

  public static String[] getAnnotationNames(PsiModifierListOwner psi) {
    List<String> annoNames = ContainerUtil.newArrayList();
    final PsiModifierList modifierList = psi.getModifierList();
    if (modifierList instanceof GrModifierList) {
      for (GrAnnotation annotation : ((GrModifierList)modifierList).getRawAnnotations()) {
        final String name = annotation.getShortName();
        if (StringUtil.isNotEmpty(name)) {
          annoNames.add(name);
        }
      }
    }
    return ArrayUtil.toStringArray(annoNames);
  }

  public static boolean isGroovyStaticMemberStub(StubElement<?> stub) {
    StubElement<?> modifierOwner = stub instanceof GrMethodStub ? stub : stub.getParentStub();
    GrModifierListStub type = modifierOwner.findChildStubByType(GroovyElementTypes.MODIFIERS);
    if (type == null) {
      return false;
    }
    int mask = type.getModifiersFlags();
    if (hasMaskModifier(mask, PsiModifier.PRIVATE)) {
      return false;
    }
    if (hasMaskModifier(mask, PsiModifier.STATIC)) {
      return true;
    }

    StubElement parent = modifierOwner.getParentStub();
    StubElement classStub = parent == null ? null : parent.getParentStub();
    if (classStub instanceof GrTypeDefinitionStub &&
        (((GrTypeDefinitionStub)classStub).isAnnotationType() || ((GrTypeDefinitionStub)classStub).isInterface())) {
      return true;
    }
    return false;
  }

  @NotNull
  public static String getShortTypeText(@Nullable String text) {
    if (text == null) {
      return "";
    }
    int i = text.length();
    while (i - 2 >= 0 && text.charAt(i - 2) == '[' && text.charAt(i - 1) == ']') {
      i -= 2;
    }
    return PsiNameHelper.getShortClassName(text.substring(0, i)) + text.substring(i);
  }

  @Nullable
  public static String getPackageName(final GrFileStub stub) {
    for (StubElement child : stub.getChildrenStubs()) {
      if (child instanceof GrPackageDefinitionStub) {
        return ((GrPackageDefinitionStub)child).getPackageName();
      }
    }
    return null;
  }
}
