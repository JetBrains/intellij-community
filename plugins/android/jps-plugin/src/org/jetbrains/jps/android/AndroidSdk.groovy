package org.jetbrains.jps.android

import org.jetbrains.jps.JavaSdk
import org.jetbrains.jps.JavaSdkImpl
import org.jetbrains.jps.Project

/**
 * @author Eugene.Kudelevsky
 */
class AndroidSdk extends JavaSdk {
  final String sdkPath;
  final String buildTargetHashString;
  final String javaSdkName;

  AndroidSdk(Project project, String name, String sdkPath, String javaSdkName, String buildTargetHashString) {
    super(project, name, '', {})
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

  @Override
  String getHomePath() {
    def javaSdk = project.sdks[javaSdkName]
    return javaSdk instanceof JavaSdkImpl ? javaSdk.getHomePath() : null
  }

  @Override
  String getVersion() {
    def javaSdk = project.sdks[javaSdkName]
    return javaSdk instanceof JavaSdkImpl ? javaSdk.getVersion() : null
  }
}
