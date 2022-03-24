// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream;
import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.indexing.IndexExtension;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.impl.forward.AbstractForwardIndexAccessor;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;

final class ValueSerializationChecker<Value, Input> {
  private static final Logger LOG = Logger.getInstance(ValueSerializationChecker.class);

  private final @NotNull DataExternalizer<Value> myValueExternalizer;
  private final @NotNull IndexId<?, Value> myIndexId;
  private final @NotNull ValueSerializationProblemReporter myProblemReporter;

  ValueSerializationChecker(@NotNull IndexExtension<?, Value, ?> extension,
                            @NotNull ValueSerializationProblemReporter reporter) {
    myValueExternalizer = extension.getValueExternalizer();
    myIndexId = extension.getName();
    myProblemReporter = reporter;
  }

  void checkValueSerialization(@NotNull Map<?, Value> data, @NotNull Input input) {
    Exception problem = getValueSerializationProblem(data, input);
    if (problem != null) {
      myProblemReporter.reportProblem(problem);
    }
  }

  private @Nullable Exception getValueSerializationProblem(@NotNull Map<?, Value> data, @NotNull Input input) {
    for (Map.Entry<?, Value> e : data.entrySet()) {
      final Value value = e.getValue();
      if (!(Comparing.equal(value, value) && (value == null || value.hashCode() == value.hashCode()))) {
        return new Exception("Index " + myIndexId + " violates equals / hashCode contract for Value parameter");
      }

      try {
        ByteArraySequence sequence = AbstractForwardIndexAccessor.serializeValueToByteSeq(value,
                                                                                          myValueExternalizer,
                                                                                          4);
        Value deserializedValue = sequence == null ? null : myValueExternalizer.read(new DataInputStream(sequence.toInputStream()));

        if (!(Comparing.equal(value, deserializedValue) && (value == null || value.hashCode() == deserializedValue.hashCode()))) {
          LOG.error(("Index " + myIndexId + " deserialization violates equals / hashCode contract for Value parameter") +
                    " while indexing " + input +
                    ". Original value: '" + value +
                    "'; Deserialized value: '" + deserializedValue + "'");
        }
      }
      catch (IOException ex) {
        return ex;
      }
    }
    return null;
  }

  static final ValueSerializationProblemReporter DEFAULT_SERIALIZATION_PROBLEM_REPORTER = ex -> LOG.error(ex);
}
