// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs.elements;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiNameHelper;
import com.intellij.psi.stubs.IndexSink;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.ArrayUtilRt;
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
  public GrReferenceListStub createStub(@NotNull T psi, StubElement<?> parentStub) {
    List<String> refNames = new ArrayList<>();
    for (GrCodeReferenceElement element : psi.getReferenceElementsGroovy()) {
      final String name = GrStubUtils.getReferenceName(element);
      if (StringUtil.isNotEmpty(name)) {
        refNames.add(name);
      }
    }
    return new GrReferenceListStub(parentStub, this, ArrayUtilRt.toStringArray(refNames));

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

