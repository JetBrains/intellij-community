// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ilyas
 */
public class GrFileStub extends PsiFileStubImpl<GroovyFile> {
  private final @NotNull String[] myAnnotations;
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

  public GrFileStub(StringRef name, boolean isScript, @NotNull String[] annotations) {
    super(null);
    myName = name;
    this.isScript = isScript;
    myAnnotations = annotations;
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
}
