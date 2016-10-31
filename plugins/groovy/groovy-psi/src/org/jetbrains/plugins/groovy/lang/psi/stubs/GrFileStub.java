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

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.reference.SoftReference;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ilyas
 */
public class GrFileStub extends PsiFileStubImpl<GroovyFile> {
  private final @NotNull String[] myAnnotations;
  private final StringRef myName;
  private final boolean isScript;
  private final @NotNull String[] myDeclarationStrings;

  private SoftReference<GrVariableDeclaration[]> myDeclarations;

  public GrFileStub(GroovyFile file) {
    super(file);
    myName = StringRef.fromString(file.getViewProvider().getVirtualFile().getNameWithoutExtension());
    isScript = file.isScript();
    final GrPackageDefinition definition = file.getPackageDefinition();
    if (definition != null) {
      myAnnotations = GrStubUtils.getAnnotationNames(definition);
    }
    else {
      myAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;
    }
    myDeclarationStrings = ContainerUtil.map(
      file.getAnnotatedScriptDeclarations(),
      declaration -> declaration.getText(),
      ArrayUtil.EMPTY_STRING_ARRAY
    );
  }

  public GrFileStub(StringRef name, boolean isScript, @NotNull String[] annotations, @NotNull String[] declarationStrings) {
    super(null);
    myName = name;
    this.isScript = isScript;
    myAnnotations = annotations;
    myDeclarationStrings = declarationStrings;
  }

  @NotNull
  @Override
  public IStubFileElementType getType() {
    return GroovyParserDefinition.GROOVY_FILE;
  }

  public StringRef getName() {
    return myName;
  }

  public boolean isScript() {
    return isScript;
  }

  @NotNull
  public String[] getAnnotations() {
    return myAnnotations;
  }

  @NotNull
  public String[] getDeclarationStrings() {
    return myDeclarationStrings;
  }

  @NotNull
  public GrVariableDeclaration[] getAnnotatedScriptDeclarations() {
    String[] declarationStrings = getDeclarationStrings();
    if (declarationStrings.length == 0) return GrVariableDeclaration.EMPTY_ARRAY;

    GrVariableDeclaration[] declarations = SoftReference.dereference(myDeclarations);
    if (declarations == null) {
      GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
      GroovyFile psi = getPsi();
      declarations = ContainerUtil.map(
        declarationStrings,
        declarationString -> factory.createVariableDeclarationFromText(declarationString, psi),
        GrVariableDeclaration.EMPTY_ARRAY
      );
      myDeclarations = new SoftReference<>(declarations);
    }
    return declarations;
  }
}
