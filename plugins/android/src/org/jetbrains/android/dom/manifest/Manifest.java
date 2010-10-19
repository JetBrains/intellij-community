package org.jetbrains.android.dom.manifest;

import com.intellij.util.xml.DefinesXml;
import com.intellij.util.xml.GenericAttributeValue;

import java.util.List;

/**
 * @author yole
 */
@DefinesXml
public interface Manifest extends ManifestElement {
  Application getApplication();

  GenericAttributeValue<String> getPackage();

  List<Instrumentation> getInstrumentations();

  List<Permission> getPermissions();

  List<PermissionGroup> getPermissionGroups();

  List<PermissionTree> getPermissionTrees();

  List<UsesPermission> getUsesPermissions();
}
