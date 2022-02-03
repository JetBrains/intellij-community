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
package com.intellij.vcs.log.impl;

import com.intellij.ide.caches.CachesInvalidator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.vcs.log.VcsLogBundle;
import com.intellij.vcs.log.util.PersistentUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VcsLogCachesInvalidator extends CachesInvalidator {
  private static final Logger LOG = Logger.getInstance(VcsLogCachesInvalidator.class);

  public synchronized boolean isValid() {
    if (PersistentUtil.getCorruptionMarkerFile().exists()) {
      boolean deleted = FileUtil.deleteWithRenaming(PersistentUtil.LOG_CACHE);
      if (!deleted) {
        // if could not delete caches, ensure that corruption marker is still there
        FileUtil.createIfDoesntExist(PersistentUtil.getCorruptionMarkerFile());
      }
      else {
        LOG.info("Deleted Vcs Log caches at " + PersistentUtil.LOG_CACHE);
      }
      return deleted;
    }
    return true;
  }

  @Override
  public void invalidateCaches() {
    if (PersistentUtil.LOG_CACHE.exists()) {
      String[] children = PersistentUtil.LOG_CACHE.list();
      if (!ArrayUtil.isEmpty(children)) {
        FileUtil.createIfDoesntExist(PersistentUtil.getCorruptionMarkerFile());
      }
    }
  }

  @Override
  public @Nullable String getDescription() {
    return VcsLogBundle.message("vcs.log.clear.caches.checkbox.description");
  }

  @Override
  public @Nullable Boolean optionalCheckboxDefaultValue() {
    return Boolean.FALSE;
  }

  @NotNull
  public static VcsLogCachesInvalidator getInstance() {
    return EP_NAME.findExtension(VcsLogCachesInvalidator.class);
  }
}
