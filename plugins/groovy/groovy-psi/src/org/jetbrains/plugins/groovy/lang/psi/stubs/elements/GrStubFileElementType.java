/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.impl.java.stubs.hierarchy.IndexTree;
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.indexing.IndexingDataKeys;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.hierarchy.GrStubIndexer;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnnotatedMemberIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrFullScriptNameIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrScriptClassNameIndex;

import java.io.IOException;

/**
 * @author ilyas
 */
public class GrStubFileElementType extends IStubFileElementType<GrFileStub> {

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
    };
  }

  @Override
  public int getStubVersion() {
    return super.getStubVersion() + 25;
  }

  @Override
  @NotNull
  public String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void indexStub(@NotNull PsiFileStub stub, @NotNull IndexSink sink) {
    super.indexStub(stub, sink);
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

    Integer fileId = stub.getUserData(IndexingDataKeys.VIRTUAL_FILE_ID);
    if (fileId == null) return;
    IndexTree.Unit unit = GrStubIndexer.translate(fileId, stub);
    sink.occurrence(JavaStubIndexKeys.UNITS, unit);
  }

}
