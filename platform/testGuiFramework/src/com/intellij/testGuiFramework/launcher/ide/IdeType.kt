package com.intellij.testGuiFramework.launcher.ide

/**
 * @author Sergey Karashevich
 */
enum class IdeType(val id: String, val buildTypeExtId: String, val platformPrefix: String, val ideJarName: String, val mainModule: String){
    IDEA_COMMUNITY(id = "IdeaIC", buildTypeExtId = "ijplatform_master_Idea_Installers", platformPrefix = "Idea", ideJarName = "idea.jar", mainModule = "community-main"),
    IDEA_ULTIMATE(id = "IdeaIU", buildTypeExtId = "ijplatform_master_Idea_Installers", platformPrefix = "", ideJarName = "idea.jar", mainModule = "main"),
    WEBSTORM(id = "WebStorm-EAP", buildTypeExtId = "bt3948", platformPrefix = "WebStorm", ideJarName = "webstorm.jar", mainModule = "main_WebStorm")
}
