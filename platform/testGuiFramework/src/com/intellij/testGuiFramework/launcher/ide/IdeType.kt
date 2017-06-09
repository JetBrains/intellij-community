package com.intellij.testGuiFramework.launcher.ide

/**
 * @author Sergey Karashevich
 */
enum class IdeType(val id: String, val buildTypeExtId: String, val platformPrefix: String, val ideJarName: String, val mainModule: String) {
  IDEA_COMMUNITY(id = "IdeaIC", buildTypeExtId = "ijplatform_master_Idea_Installers", platformPrefix = "Idea", ideJarName = "idea.jar", mainModule = "community-main"),
  IDEA_ULTIMATE(id = "IdeaIU", buildTypeExtId = "ijplatform_master_Idea_Installers", platformPrefix = "", ideJarName = "idea.jar", mainModule = "main"),
  WEBSTORM(id = "WebStorm-EAP", buildTypeExtId = "bt3948", platformPrefix = "WebStorm", ideJarName = "webstorm.jar", mainModule = "main_WebStorm"),
  PYCHARM(id = "null", buildTypeExtId = "null", platformPrefix = "Python", ideJarName = "pycharm.jar", mainModule = "main_pycharm"),
  PYCHARM_COMMUNITY(id = "null", buildTypeExtId = "null", platformPrefix = "PyCharmCore", ideJarName = "pycharm.jar", mainModule = "main_pycharm_ce"),
  PYCHARM_EDU(id = "null", buildTypeExtId = "null", platformPrefix = "PyCharmEdu", ideJarName = "pycharm.jar", mainModule = "main_pycharm_edu"),
  ANDROID_STUDIO(id = "null", buildTypeExtId = "null", platformPrefix = "AndroidStudio", ideJarName = "idea.jar", mainModule = "main_AndroidStudio"),
  PHPSTORM(id = "PhpStorm-EAP", buildTypeExtId = "bt1028s", platformPrefix = "PhpStorm", ideJarName = "phpstorm.jar", mainModule = "main_phpstorm"),
}
