package xxx;

import com.intellij.openapi.util.io.FileUtilRt ;

public class MyAction {
  public void actionPerformed() {
    FileUtilRt.toSystemDependentName("myPath");
  }
}
