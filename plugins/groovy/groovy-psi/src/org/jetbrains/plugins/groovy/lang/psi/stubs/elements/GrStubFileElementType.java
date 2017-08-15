/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullScriptNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrScriptClassNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrStubFileElementType extends IStubFileElementType<GrFileStub> {
  public static final int STUB_VERSION = 37;

  public GrStubFileElementType(Language language) {
    super(language);
  }

  @Override
  public StubBuilder getBuilder() {
    return new DefaultStubBuilder() {
      @NotNull
      @Override
      protected StubElement createStubForFile(@NotNull final PsiFile file) {
        if (file instanceof GroovyFile) {
          return new GrFileStub((GroovyFile)file);
        }

        return super.createStubForFile(file);
      }

      @Override
      public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
        IElementType childType = node.getElementType();
        IElementType parentType = parent.getElementType();
        if (childType == GroovyElementTypes.PARAMETER && parentType != GroovyElementTypes.PARAMETERS_LIST) {
          return true;
        }
        if (childType == GroovyElementTypes.PARAMETERS_LIST && !(parent.getPsi() instanceof GrMethod)) {
          return true;
        }
        if (childType == GroovyElementTypes.MODIFIERS) {
          if (parentType == GroovyElementTypes.CLASS_INITIALIZER) {
            return true;
          }
          if (parentType == GroovyElementTypes.VARIABLE_DEFINITION && !GroovyElementTypes.VARIABLE_DEFINITION.shouldCreateStub(parent)) {
            return true;
          }
        }

        return false;
      }
    };
  }

  @Override
  public int getStubVersion() {
    return super.getStubVersion() + STUB_VERSION;
  }

  @Override
  @NotNull
  public String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void serialize(@NotNull final GrFileStub stub, @NotNull final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName().toString());
    dataStream.writeBoolean(stub.isScript());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
  }

  @NotNull
  @Override
  public GrFileStub deserialize(@NotNull final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef name = dataStream.readName();
    boolean isScript = dataStream.readBoolean();
    return new GrFileStub(name, isScript, GrStubUtils.readStringArray(dataStream));
  }

  @Override
  public void indexStub(@NotNull GrFileStub stub, @NotNull IndexSink sink) {
    String name = stub.getName().toString();
    if (stub.isScript() && name != null) {
      sink.occurrence(GrScriptClassNameIndex.KEY, name);
      final String pName = GrStubUtils.getPackageName(stub);
      final String fqn = StringUtil.isEmpty(pName) ? name : pName + "." + name;
      sink.occurrence(GrFullScriptNameIndex.KEY, fqn.hashCode());
    }

    for (String anno : stub.getAnnotations()) {
      sink.occurrence(GrAnnotatedMemberIndex.KEY, anno);
    }
  }

}
