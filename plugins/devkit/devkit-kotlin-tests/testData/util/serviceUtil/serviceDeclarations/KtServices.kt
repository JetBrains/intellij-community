@file:Suppress("NO_REFLECTION_IN_CLASS_PATH")

package serviceDeclarations

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project


/**
 * APP_AND_PROJECT
 */
@Service(Service.Level.APP, Service.Level.PROJECT)
class KtLightServiceAppAndProjectLevelArray {
  companion object {
    @JvmStatic
    fun getInstance(): KtLightServiceAppAndProjectLevelArray {
      return ApplicationManager.getApplication().getService(KtLightServiceAppAndProjectLevelArray::class.java)
    }
  }
}


/**
 * APP_AND_PROJECT
 */
@Service(Service.Level.APP, Service.Level.PROJECT, Service.Level.APP, Service.Level.PROJECT)
class KtLightServiceAppAndProjectLevelRepeatedArray {
  companion object {
    @JvmStatic
    fun getInstance(): KtLightServiceAppAndProjectLevelRepeatedArray {
      return ApplicationManager.getApplication().getService(KtLightServiceAppAndProjectLevelRepeatedArray::class.java)
    }
  }
}

/**
 * APP
 */
@Service(Service.Level.APP)
class KtLightServiceAppLevel {
  companion object {
    @JvmStatic
    fun getInstance(): KtLightServiceAppLevel {
      return ApplicationManager.getApplication().getService(KtLightServiceAppLevel::class.java)
    }
  }
}

/**
 * APP
 */
@Service
class KtLightServiceEmpty {
  companion object {
    @JvmStatic
    fun getInstance(): KtLightServiceEmpty {
      return ApplicationManager.getApplication().getService(KtLightServiceEmpty::class.java)
    }
  }
}

/**
 * PROJECT
 */
@Service(Service.Level.PROJECT)
class KtLightServiceProjectLevel {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): KtLightServiceProjectLevel {
      return project.getService(KtLightServiceProjectLevel::class.java)
    }
  }
}

/**
 * APP
 */
class KtRegisteredApplicationService {
  companion object {
    @JvmStatic
    fun getInstance(): KtRegisteredApplicationService {
      return ApplicationManager.getApplication().getService(KtRegisteredApplicationService::class.java)
    }
  }
}

/**
 * MODULE
 */
class KtRegisteredModuleService {
  companion object {
    @JvmStatic
    fun getInstance(module: Module): KtRegisteredModuleService {
      return module.getService(KtRegisteredModuleService::class.java)
    }
  }
}

/**
 * PROJECT
 */
class KtRegisteredProjectService {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): KtRegisteredProjectService {
      return project.getService(KtRegisteredProjectService::class.java)
    }
  }
}

class KtNonService {
  companion object {
    @JvmStatic
    fun getInstance(): KtNonService {
      return KtNonService()
    }
  }
}






