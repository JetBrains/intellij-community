// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant;

import com.intellij.buildfiles.ForcedBuildFileAttribute;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 */
public final class ForcedAntFileAttribute extends FileAttribute {
  private static final Logger LOG = Logger.getInstance(ForcedAntFileAttribute.class);

  private static final String ANT_ID = "ant";

  private static final ForcedAntFileAttribute ourAttribute = new ForcedAntFileAttribute();
  private static final Key<Boolean> ourAntFileMarker = Key.create("_forced_ant_attribute_");

  public ForcedAntFileAttribute() {
    super("_forced_ant_attribute_", 1, true);
  }

  public static boolean isAntFile(VirtualFile file) {
    String id = ForcedBuildFileAttribute.getFrameworkIdOfBuildFile(file);
    return ANT_ID.equals(id) || (StringUtil.isEmpty(id) && isAntFileOld(file));
  }

  public static boolean mayBeAntFile(VirtualFile file) {
    String id = ForcedBuildFileAttribute.getFrameworkIdOfBuildFile(file);
    return StringUtil.isEmpty(id) || ANT_ID.equals(id);
  }

  private static boolean isAntFileOld(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      try (DataInputStream is = ourAttribute.readFileAttribute(file)) {
        if (is != null) {
          return is.readBoolean();
        }
        return false;
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    return Boolean.TRUE.equals(file.getUserData(ourAntFileMarker));
  }

  public static void forceAntFile(VirtualFile file, boolean value) {
    ForcedBuildFileAttribute.forceFileToFramework(file, ANT_ID, value);
  }
}
