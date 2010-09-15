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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.lang.Language;
import com.intellij.psi.StubBuilder;
import com.intellij.psi.stubs.*;
import com.intellij.psi.tree.IStubFileElementType;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFileStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GroovyFileStubBuilder;
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

  public StubBuilder getBuilder() {
    return new GroovyFileStubBuilder();
  }

  @Override
  public int getStubVersion() {
    return super.getStubVersion() + 7;
  }

  public String getExternalId() {
    return "groovy.FILE";
  }

  @Override
  public void indexStub(PsiFileStub stub, IndexSink sink) {
    super.indexStub(stub, sink);
  }

  @Override
  public void serialize(final GrFileStub stub, final StubOutputStream dataStream) throws IOException {
    dataStream.writeName(stub.getPackageName().toString());
    dataStream.writeName(stub.getName().toString());
    dataStream.writeBoolean(stub.isScript());
    GrStubUtils.writeStringArray(dataStream, stub.getAnnotations());
  }

  @Override
  public GrFileStub deserialize(final StubInputStream dataStream, final StubElement parentStub) throws IOException {
    StringRef packName = dataStream.readName();
    StringRef name = dataStream.readName();
    boolean isScript = dataStream.readBoolean();
    return new GrFileStub(packName, name, isScript, GrStubUtils.readStringArray(dataStream));
  }

  public void indexStub(GrFileStub stub, IndexSink sink) {
    String name = stub.getName().toString();
    if (stub.isScript() && name != null) {
      sink.occurrence(GrScriptClassNameIndex.KEY, name);
      final String pName = stub.getPackageName().toString();
      final String fqn = pName == null || pName.length() == 0 ? name : pName + "." + name;
      sink.occurrence(GrFullScriptNameIndex.KEY, fqn.hashCode());
    }

    for (String anno : stub.getAnnotations()) {
      sink.occurrence(GrAnnotatedMemberIndex.KEY, anno);
    }
  }

}
