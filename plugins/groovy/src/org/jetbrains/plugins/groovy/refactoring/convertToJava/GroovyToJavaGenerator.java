/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrConstructor;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

import java.util.*;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 03.05.2007
 */
public class GroovyToJavaGenerator {
  private static final Map<String, String> typesToInitialValues = new HashMap<String, String>();
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.refactoring.convertToJava.GroovyToJavaGenerator");

  static {
    typesToInitialValues.put("boolean", "false");
    typesToInitialValues.put("int", "0");
    typesToInitialValues.put("short", "0");
    typesToInitialValues.put("long", "0L");
    typesToInitialValues.put("byte", "0");
    typesToInitialValues.put("char", "'c'");
    typesToInitialValues.put("double", "0D");
    typesToInitialValues.put("float", "0F");
    typesToInitialValues.put("void", "");
  }

  private final Set<VirtualFile> myAllToCompile;
  private final Project myProject;

  private final boolean fullConversion;

  public GroovyToJavaGenerator(Project project, Set<VirtualFile> allToCompile, boolean fullConversion) {
    myProject = project;
    myAllToCompile = allToCompile;
    this.fullConversion = fullConversion;
  }

  public Map<String, CharSequence> generateStubs(GroovyFile file) {
    Set<String> classNames = new THashSet<String>();
    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      classNames.add(typeDefinition.getName());
    }

    final Map<String, CharSequence> output = new LinkedHashMap<String, CharSequence>();

    if (file.isScript()) {
      VirtualFile virtualFile = file.getVirtualFile();
      assert virtualFile != null;
      String fileDefinitionName = virtualFile.getNameWithoutExtension();
      if (!classNames.contains(StringUtil.capitalize(fileDefinitionName)) &&
          !classNames.contains(StringUtil.decapitalize(fileDefinitionName))) {
        final PsiClass scriptClass = file.getScriptClass();
        if (scriptClass != null) {
          generateClassStub(scriptClass, output);
        }
      }
    }

    for (final GrTypeDefinition typeDefinition : file.getTypeDefinitions()) {
      generateClassStub(GrClassSubstitutor.getSubstitutedClass(typeDefinition), output);
    }
    return output;
  }

  private void generateClassStub(PsiClass clazz, Map<String, CharSequence> output) {
    final CharSequence text = generateClass(clazz);
    final String filename = getFileNameForClass(clazz);
    output.put(filename, text);
  }

  private String getFileNameForClass(PsiClass clazz) {
    if (fullConversion) {
      return clazz.getName() + ".java";
    }
    else {
      final PsiFile containingFile = clazz.getContainingFile();
      final GrPackageDefinition packageDefinition = ((GroovyFile)containingFile).getPackageDefinition();
      return getPackageDirectory(packageDefinition) + clazz.getName() + ".java";
    }
  }

  private static String getPackageDirectory(@Nullable GrPackageDefinition packageDefinition) {
    if (packageDefinition == null) return "";

    String prefix = packageDefinition.getPackageName();
    if (prefix == null) return "";

    return prefix.replace('.', '/') + '/';
  }

  public CharSequence generateClass(@NotNull PsiClass typeDefinition) {
    try {
      StringBuilder text = new StringBuilder();
      final ClassNameProvider classNameProvider =
        fullConversion ? new GeneratorClassNameProvider() : new StubClassNameProvider(myAllToCompile);
      new ClassGenerator(myProject, classNameProvider).writeTypeDefinition(text, typeDefinition, true);
      return text;
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      LOG.error(e);
      return "";
    }
  }

  public static String getDefaultValueText(String typeCanonicalText) {
    final String result = typesToInitialValues.get(typeCanonicalText);
    if (result == null) return "null";
    return result;
  }

  public static String generateMethodStub(@NotNull PsiMethod method) {
    if (!(method instanceof GroovyPsiElement)) {
      return method.getText();
    }

    final ClassGenerator generator =
      new ClassGenerator(method.getProject(), new StubClassNameProvider(Collections.<VirtualFile>emptySet()));
    final StringBuilder buffer = new StringBuilder();
    if (method instanceof GrConstructor) {
      generator.writeConstructor(buffer, (GrConstructor)method, false);
    }
    else {
      generator.writeMethod(buffer, method, method.getParameterList().getParameters());
    }
    return buffer.toString();
  }

}
