package pkg

import com.intellij.openapi.project.Project

class PackagedExt {
  var <warning descr="Do not use Project as a field in extension">p</warning>: Project? = null

  class Nested {
    var <warning descr="Do not use Project as a field in extension">p</warning>: Project? = null
  }

  class NotRegistered {
    var p: Project? = null
  }
}

class PackagedForClass {
  var p: Project? = null
}

class PackagedTarget {
  var p: Project? = null
}

class PackagedProvider {
  var <warning descr="Do not use Project as a field in extension">p</warning>: Project? = null
}

class PackagedSettings {
  var p: Project? = null
}

open class PackagedServiceApi {
  var p: Project? = null
}

class PackagedLanguageService : PackagedServiceApi() {
  var <warning descr="Do not use Project as a field in extension">p2</warning>: Project? = null
}
