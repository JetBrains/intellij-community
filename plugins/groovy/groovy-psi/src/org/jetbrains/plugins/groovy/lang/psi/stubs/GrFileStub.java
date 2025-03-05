// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

public class GrFileStub extends PsiFileStubImpl<GroovyFile> {
  private final String @NotNull [] myAnnotations;
  private final StringRef myName;
  private final boolean isScript;

  public GrFileStub(GroovyFile file) {
    super(file);
    myName = StringRef.fromString(file.getViewProvider().getVirtualFile().getNameWithoutExtension());
    isScript = file.isScript();
    final GrPackageDefinition definition = file.getPackageDefinition();
    if (definition != null) {
      myAnnotations = GrStubUtils.getAnnotationNames(definition);
    }
    else {
      myAnnotations = ArrayUtilRt.EMPTY_STRING_ARRAY;
    }
  }

  public GrFileStub(StringRef name, boolean isScript, String @NotNull [] annotations) {
    super(null);
    myName = name;
    this.isScript = isScript;
    myAnnotations = annotations;
  }

  @Override
  public @NotNull IStubFileElementType<?> getType() {
    return GroovyParserDefinition.GROOVY_FILE;
  }

  public StringRef getName() {
    return myName;
  }

  public boolean isScript() {
    return isScript;
  }

  public String @NotNull [] getAnnotations() {
    return myAnnotations;
  }
}
