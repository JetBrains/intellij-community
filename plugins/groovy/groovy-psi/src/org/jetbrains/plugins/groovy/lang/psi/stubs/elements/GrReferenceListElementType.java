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
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrStubUtils;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author ilyas
 */
public abstract class GrReferenceListElementType<T extends GrReferenceList> extends GrStubElementType<GrReferenceListStub, T> {

  public GrReferenceListElementType(final String debugName) {
    super(debugName);
  }

  @NotNull
  @Override
  public GrReferenceListStub createStub(@NotNull T psi, StubElement parentStub) {
    List<String> refNames = new ArrayList<>();
    for (GrCodeReferenceElement element : psi.getReferenceElementsGroovy()) {
      final String name = GrStubUtils.getReferenceName(element);
      if (StringUtil.isNotEmpty(name)) {
        refNames.add(name);
      }
    }
    return new GrReferenceListStub(parentStub, this, ArrayUtil.toStringArray(refNames));

  }

  @Override
  public void serialize(@NotNull GrReferenceListStub stub, @NotNull StubOutputStream dataStream) throws IOException {
    GrStubUtils.writeStringArray(dataStream, stub.getBaseClasses());
  }

  @Override
  @NotNull
  public GrReferenceListStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
    return new GrReferenceListStub(parentStub, this, GrStubUtils.readStringArray(dataStream));
  }

  @Override
  public void indexStub(@NotNull GrReferenceListStub stub, @NotNull IndexSink sink) {
    for (String name : stub.getBaseClasses()) {
      if (name != null) {
        sink.occurrence(GrDirectInheritorsIndex.KEY, PsiNameHelper.getShortClassName(name));
      }
    }
  }

  @Override
  public boolean isLeftBound() {
    return true;
  }
}

