package com.sample;

import com.intellij.openapi.actionSystem.*;
import static com.intellij.openapi.actionSystem.ActionUpdateThread.*;

// plain
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">AwareError</warning> implements ActionUpdateThreadAware {
}

class AwareNoError implements ActionUpdateThreadAware {
  public ActionUpdateThread getActionUpdateThread() { return EDT; }
}

// inherited
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">AwareChildError</warning> extends AwareError {}
class AwareChildNoError extends AwareNoError {}

// anonymous
class Holder {
  ActionUpdateThreadAware awareError = new <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">AwareError</warning>() {
  };

  ActionUpdateThreadAware awareNoError = new AwareError() {
    public ActionUpdateThread getActionUpdateThread() { return EDT; }
  };
}

// default method (applicable)
interface WithDefaultMethod extends ActionUpdateThreadAware {
  default ActionUpdateThread getActionUpdateThread() { return BGT; }
}

class AwareNoErrorDueDefault implements WithDefaultMethod {
}
