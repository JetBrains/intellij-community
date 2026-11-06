package pkg;

import com.intellij.openapi.project.Project;

public class PackagedExt {
  Project <warning descr="Do not use Project as a field in extension">p</warning>;

  public static class Nested {
    Project <warning descr="Do not use Project as a field in extension">p</warning>;
  }

  public static class NotRegistered {
    Project p;
  }
}

class PackagedForClass {
  Project p;
}

class PackagedTarget {
  Project p;
}

class PackagedProvider {
  Project <warning descr="Do not use Project as a field in extension">p</warning>;
}

class PackagedSettings {
  Project p;
}

class PackagedServiceApi {
  Project p;
}

class PackagedLanguageService extends PackagedServiceApi {
  Project <warning descr="Do not use Project as a field in extension">p2</warning>;
}
