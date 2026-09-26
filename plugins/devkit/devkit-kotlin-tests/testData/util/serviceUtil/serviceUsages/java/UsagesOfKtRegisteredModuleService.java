import com.intellij.openapi.module.Module;

import serviceDeclarations.KtRegisteredModuleService;

class MyClazz16 {
  void foo16(Module module) {
    Object obj = <caret>KtRegisteredModuleService.getInstance(module);
  }
}