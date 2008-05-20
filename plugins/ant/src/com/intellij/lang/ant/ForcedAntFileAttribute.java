package com.intellij.lang.ant;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 30, 2008
 */
public class ForcedAntFileAttribute extends FileAttribute {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.ForcedAntFileAttribute");
  
  private static final ForcedAntFileAttribute ourAttribute = new ForcedAntFileAttribute();
  private static Key<Boolean> ourAntFileMarker = Key.create("_forced_ant_attribute_");
 
  public ForcedAntFileAttribute() {
    super("_forced_ant_attribute_", 1);
  }
  
  public static boolean isAntFile(VirtualFile file) {
    if (file instanceof NewVirtualFile) {
      final DataInputStream is = ourAttribute.readAttribute(file);
      if (is != null) {
        try {
          try {
            return is.readBoolean();
          }
          finally {
            is.close();
          }
        }
        catch (IOException e) {
          LOG.error(e);
        }
      }
      return false;
    }
    return Boolean.TRUE.equals(file.getUserData(ourAntFileMarker));
  }
  
  public static void forceAntFile(VirtualFile file, boolean value) {
    if (file instanceof NewVirtualFile) {
      final DataOutputStream os = ourAttribute.writeAttribute(file);
      try {
        try {
          os.writeBoolean(value);
        }
        finally {
          os.close();
        }
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    else {
      file.putUserData(ourAntFileMarker, Boolean.valueOf(value));
    }
  }
}
