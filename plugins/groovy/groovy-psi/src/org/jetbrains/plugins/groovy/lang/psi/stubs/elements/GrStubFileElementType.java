// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullScriptNameStringIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrScriptClassNameIndex;

import java.io.IOException;

public class GrStubFileElementType extends IStubFileElementType<GrFileStub> {

  public static final int STUB_VERSION = 51;

  public GrStubFileElementType(Language language) {
    super(language);
  }

  @Override
  public StubBuilder getBuilder() {
    return new DefaultStubBuilder() {
      @Override
      protected @NotNull StubElement createStubForFile(final @NotNull PsiFile file) {
        if (file instanceof GroovyFile) {
          return new GrFileStub((GroovyFile)file);
        }

        return super.createStubForFile(file);
      }

      @Override
      public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
        IElementType childType = node.getElementType();
        IElementType parentType = parent.getElementType();
        if (childType == GroovyStubElementTypes.PARAMETER && parentType != GroovyEmptyStubElementTypes.PARAMETER_LIST) {
          return true;
        }
        if (childType == GroovyEmptyStubElementTypes.PARAMETER_LIST && !(parent.getPsi() instanceof GrMethod || parentType == GroovyStubElementTypes.RECORD_TYPE_DEFINITION)) {
          return true;
        }
        if (childType == GroovyStubElementTypes.MODIFIER_LIST) {
          if (parentType == GroovyElementTypes.CLASS_INITIALIZER) {
            return true;
          }
          if (parentType == GroovyStubElementTypes.VARIABLE_DECLARATION && !GroovyStubElementTypes.VARIABLE_DECLARATION.shouldCreateStub(parent)) {
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
  public @NotNull String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void serialize(final @NotNull GrFileStub stub, final @NotNull StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getName().toString());
    dataStream.writeBoolean(stub.isScript());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
  }

  @Override
  public @NotNull GrFileStub deserialize(final @NotNull StubInputStream dataStream, final StubElement parentStub) throws IOException {
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
      sink.occurrence(GrFullScriptNameStringIndex.KEY, fqn);
    }

    for (String anno : stub.getAnnotations()) {
      sink.occurrence(GrAnnotatedMemberIndex.KEY, anno);
    }
  }
}
