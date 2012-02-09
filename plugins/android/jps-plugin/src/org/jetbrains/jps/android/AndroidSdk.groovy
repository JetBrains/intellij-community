package org.jetbrains.jps.android

import org.jetbrains.jps.JavaSdk
import org.jetbrains.jps.Project
import org.jetbrains.jps.Sdk
import org.jetbrains.jps.JavaSdkImpl

/**
 * @author Eugene.Kudelevsky
 */
class AndroidSdk extends Sdk implements JavaSdk {
  final String sdkPath;
  final String buildTargetHashString;
  final String javaSdkName;

  AndroidSdk(Project project, String name, String sdkPath, String javaSdkName, String buildTargetHashString) {
    super(project, name, {})
    this.sdkPath = sdkPath
    this.buildTargetHashString = buildTargetHashString
    this.javaSdkName = javaSdkName;
  }

  @Override
  String getJavacExecutable() {
    def javaSdk = project.sdks[javaSdkName]
    return javaSdk instanceof JavaSdkImpl ? javaSdk.getJavacExecutable() : null
  }

  @Override
  String getJavaExecutable() {
    def javaSdk = project.sdks[javaSdkName]
    return javaSdk instanceof JavaSdkImpl ? javaSdk.getJavaExecutable() : null
  }
}
