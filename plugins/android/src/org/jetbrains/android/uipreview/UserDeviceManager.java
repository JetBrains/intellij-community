package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.prefs.AndroidLocation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceParser;
import com.android.utils.ILogger;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class UserDeviceManager implements Disposable {
  private final VirtualFileAdapter myListener;
  private boolean myUserDevicesParsed;
  private File myUserDevicesFile;

  private static Set<String> ourWatchedRoots = new HashSet<String>();

  public UserDeviceManager() {
    myListener = new VirtualFileAdapter() {
      @Override
      public void contentsChanged(VirtualFileEvent event) {
        final VirtualFile file = event.getFile();

        if (myUserDevicesFile != null && SdkConstants.FN_DEVICES_XML.equals(file.getName()) &&
            FileUtil.pathsEqual(FileUtil.toSystemIndependentName(myUserDevicesFile.getPath()),
                                file.getPath())) {
          userDevicesChanged();
        }
      }
    };
    LocalFileSystem.getInstance().addVirtualFileListener(myListener);
  }

  protected void userDevicesChanged() {
  }

  // we need this because we cannot use DeviceManager.getUserDevices():
  // user devices are remembered in static field and cannot be reset
  @NotNull
  public List<Device> parseUserDevices(@NotNull ILogger logger) {
    final File userDevicesFile = getUserDevicesFile(logger);

    if (userDevicesFile == null) {
      return Collections.emptyList();
    }
    final VirtualFile vUserDeviceFile = LocalFileSystem.getInstance().findFileByIoFile(userDevicesFile);

    if (vUserDeviceFile == null) {
      return Collections.emptyList();
    }
    final ArrayList<Device> userDevices = new ArrayList<Device>();

    try {
      if (userDevicesFile.exists()) {
        userDevices.addAll(DeviceParser.parse(userDevicesFile));
      }
    }
    catch (SAXException e) {
      if (myUserDevicesParsed) {
        logger.error(e, "Error parsing " + userDevicesFile.getAbsolutePath());
      }
      else {
        final String newName = userDevicesFile.getAbsoluteFile() + ".old";
        File renamedConfig = new File(newName);
        int i = 0;

        while (renamedConfig.exists()) {
          renamedConfig = new File(newName + '.' + i);
          i++;
        }
        logger.error(e, "Error parsing " + userDevicesFile.getAbsolutePath() +
                           ", backing up to " + renamedConfig.getAbsolutePath());

        if (!userDevicesFile.renameTo(renamedConfig)) {
          logger.error(e, "Cannot rename file " + userDevicesFile.getAbsolutePath() + " to " + renamedConfig.getAbsolutePath());
        }
      }
    }
    catch (ParserConfigurationException e) {
      logger.error(null, "Error parsing " + userDevicesFile.getAbsolutePath());
    }
    catch (IOException e) {
      logger.error(null, "Error parsing " + userDevicesFile.getAbsolutePath());
    }
    finally {
      myUserDevicesParsed = true;
    }
    return userDevices;
  }

  private File getUserDevicesFile(ILogger logger) {
    if (myUserDevicesFile == null) {
      try {
        String myFolderToStoreDevicesXml = AndroidLocation.getFolder();
        myUserDevicesFile = new File(myFolderToStoreDevicesXml, SdkConstants.FN_DEVICES_XML);
      }
      catch (AndroidLocation.AndroidLocationException e) {
        logger.warning("Couldn't load user devices: " + e.getMessage());
        myUserDevicesFile = null;
        return null;
      }
      addDevicesXmlWatchedRootIfNecessary(myUserDevicesFile);
    }
    return myUserDevicesFile;
  }

  private static void addDevicesXmlWatchedRootIfNecessary(@NotNull File root) {
    final String path = FileUtil.toSystemIndependentName(root.getPath());

    if (ourWatchedRoots.add(path)) {
      LocalFileSystem.getInstance().addRootToWatch(path, true);
    }
  }

  @Override
  public void dispose() {
    LocalFileSystem.getInstance().removeVirtualFileListener(myListener);
  }
}
