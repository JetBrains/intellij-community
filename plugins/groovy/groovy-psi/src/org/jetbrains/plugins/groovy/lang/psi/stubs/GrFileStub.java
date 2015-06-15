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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.PsiFileStubImpl;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author ilyas
 */
public class GrFileStub extends PsiFileStubImpl<GroovyFile> {
  private final String[] myAnnotations;
  private final StringRef myName;
  private final boolean isScript;

  public GrFileStub(GroovyFile file) {
    super(file);
    myName = StringRef.fromString(file.getViewProvider().getVirtualFile().getNameWithoutExtension());
    isScript = file.isScript();
    final GrPackageDefinition definition = file.getPackageDefinition();
    if (definition != null) {
      myAnnotations = GrStubUtils.getAnnotationNames(definition);
    } else {
      myAnnotations = ArrayUtil.EMPTY_STRING_ARRAY;
    }
  }

  public GrFileStub(StringRef name, boolean isScript, String[] annotations) {
    super(null);
    myName = name;
    this.isScript = isScript;
    myAnnotations = annotations;
  }

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

  public String[] getAnnotations() {
    return myAnnotations;
  }
}
