// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch.apply;

import com.intellij.openapi.diff.impl.patch.BinaryFilePatch;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.TextFilePatch;
import com.intellij.openapi.vcs.changes.shelf.ShelvedBinaryFilePatch;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ApplyFilePatchFactory {
  private ApplyFilePatchFactory() {
  }

  public static ApplyTextFilePatch create(final TextFilePatch patch) {
    return new ApplyTextFilePatch(patch);
  }

  public static ApplyBinaryFilePatch create(final BinaryFilePatch patch) {
    return new ApplyBinaryFilePatch(patch);
  }

  public static ApplyBinaryShelvedFilePatch create(final ShelvedBinaryFilePatch patch) {
    return new ApplyBinaryShelvedFilePatch(patch);
  }

  public static ApplyFilePatchBase<?> createGeneral(@NotNull FilePatch patch) {
    if (patch instanceof TextFilePatch) {
      return create((TextFilePatch) patch);
    } else if (patch instanceof BinaryFilePatch) {
      return create((BinaryFilePatch) patch);
    } else if (patch instanceof ShelvedBinaryFilePatch) {
      return create((ShelvedBinaryFilePatch) patch);
    }
    throw new IllegalStateException();
  }
}
