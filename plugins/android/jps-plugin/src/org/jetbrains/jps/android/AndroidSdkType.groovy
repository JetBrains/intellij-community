package org.jetbrains.jps.android

import org.jetbrains.jps.idea.SdkTypeService
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.Project

/**
 * @author Eugene.Kudelevsky
 */
class AndroidSdkType extends SdkTypeService {
  AndroidSdkType() {
    super("Android SDK");
  }

  @Override
  Sdk createSdk(Project project, String name, String version, String homePath, Node additionalData) {
    def attributes = additionalData.attributes()
    if (attributes == null) {
      return null;
    }

    def buildTargetHashString = (String)attributes.get("sdk")
    def internalJdkName = (String)attributes.get("jdk")

    if (internalJdkName == null || buildTargetHashString == null) {
      return null;
    }
    return new AndroidSdk(project, name, homePath, internalJdkName, buildTargetHashString)
  }
}
