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

package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
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
import org.jetbrains.plugins.groovy.lang.psi.impl.GrReferenceElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.modifiers.GrModifierListImpl;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2009
 */
public class GrStubUtils {
  private static final Logger LOG = Logger.getInstance(GrStubUtils.class);
  public static final int TOO_LONG = -1;

  public static void writeStringArray(StubOutputStream dataStream, String[] array) throws IOException {
    if (array.length > Byte.MAX_VALUE) {
      dataStream.writeByte(TOO_LONG);
      dataStream.writeInt(array.length);
    }
    else {
      dataStream.writeByte(array.length);
    }
    for (String s : array) {
      LOG.assertTrue(s != null);
      dataStream.writeName(s);
    }
  }

  public static String[] readStringArray(StubInputStream dataStream) throws IOException {
    int length = dataStream.readByte();
    if (length == TOO_LONG) {
      length = dataStream.readInt();
    }
    final String[] annNames = new String[length];
    for (int i = 0; i < length; i++) {
      annNames[i] = dataStream.readName().toString();
    }
    return annNames;
  }

  public static void writeNullableString(StubOutputStream dataStream, @Nullable String typeText) throws IOException {
    dataStream.writeBoolean(typeText != null);
    if (typeText != null) {
      dataStream.writeUTFFast(typeText);
    }
  }

  @Nullable
  public static String readNullableString(StubInputStream dataStream) throws IOException {
    final boolean hasTypeText = dataStream.readBoolean();
    return hasTypeText ? dataStream.readUTFFast() : null;
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
    StubElement<GrModifierList> type = modifierOwner.findChildStubByType(GroovyElementTypes.MODIFIERS);
    if (!(type instanceof GrModifierListStub)) {
      return false;
    }
    int mask = ((GrModifierListStub)type).getModifiersFlags();
    if (GrModifierListImpl.hasMaskExplicitModifier(PsiModifier.PRIVATE, mask)) {
      return false;
    }
    if (GrModifierListImpl.hasMaskExplicitModifier(PsiModifier.STATIC, mask)) {
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
