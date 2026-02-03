import com.intellij.openapi.module.Module;

import serviceDeclarations.RegisteredModuleService;

class MyClazz15 {
  void foo15(Module module) {
    Object obj = <caret>RegisteredModuleService.getInstance(module);
  }
}