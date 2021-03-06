// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

class ValueSerializationChecker<Value, Input> {
  private static final Logger LOG = Logger.getInstance(ValueSerializationChecker.class);

  private final @NotNull DataExternalizer<Value> myValueExternalizer;
  private final @NotNull IndexId<?, Value> myIndexId;

  ValueSerializationChecker(@NotNull IndexExtension<?, Value, ?> extension) {
    myValueExternalizer = extension.getValueExternalizer();
    myIndexId = extension.getName();
  }

  void checkValuesHaveProperEqualsAndHashCode(@NotNull Map<?, Value> data, @NotNull Input input) {
    for (Map.Entry<?, Value> e : data.entrySet()) {
      final Value value = e.getValue();
      if (!(Comparing.equal(value, value) && (value == null || value.hashCode() == value.hashCode()))) {
        LOG.error("Index " + myIndexId + " violates equals / hashCode contract for Value parameter");
      }

      try {
        final BufferExposingByteArrayOutputStream out = new BufferExposingByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(out);
        myValueExternalizer.save(outputStream, value);
        outputStream.close();
        final Value deserializedValue =
          myValueExternalizer.read(new DataInputStream(out.toInputStream()));

        if (!(Comparing.equal(value, deserializedValue) && (value == null || value.hashCode() == deserializedValue.hashCode()))) {
          LOG.error(("Index " + myIndexId + " deserialization violates equals / hashCode contract for Value parameter") +
                    " while indexing " + input +
                    ". Original value: '" + value +
                    "'; Deserialized value: '" + deserializedValue + "'");
        }
      }
      catch (IOException ex) {
        LOG.error(ex);
      }
    }
  }
}
