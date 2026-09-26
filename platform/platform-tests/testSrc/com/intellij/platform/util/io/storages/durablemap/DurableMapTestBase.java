// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.io.storages.durablemap;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.util.io.storages.KeyValueStoreTestBase;
import com.intellij.platform.util.io.storages.StorageTestingUtils;
import com.intellij.util.io.Unmappable;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class DurableMapTestBase<K, V, M extends DurableMap<K, V>>
  extends KeyValueStoreTestBase<K, V, M>
  implements DurableMapTestContext<K, V, M> {

  protected DurableMapTestBase(@NotNull IntFunction<? extends Map.Entry<K, V>> substrateDecoder) {
    super(substrateDecoder);
  }

  /** should return true if the map is mostly append-only */
  protected abstract boolean isAppendOnly();

  /* ============================== infrastructure ============================================================ */

  @Override
  public final boolean isAppendOnlyStorage() {
    return isAppendOnly();
  }

  @Override
  public final @NotNull M openStorage(@NotNull Path path) throws IOException {
    return factory().open(path);
  }

  @Override
  public void reopenDroppingDurableLookupPart() throws IOException {
    ((Unmappable)storage).closeAndUnsafelyUnmap();

    var mapPath = storagePath().resolveSibling(storagePath().getFileName() + ".map");
    assertTrue(Files.exists(mapPath),
               mapPath + " must exist");
    FileUtil.delete(mapPath);
    reopenStorage();
  }

  @Override
  public void reopenAfterImproperClose() throws Exception {
    StorageTestingUtils.emulateImproperClose(storage);
    //The damaged storage can crash the JVM during close. Do not close it in the fixture.
    storage = null;
    reopenStorage();
  }

}
