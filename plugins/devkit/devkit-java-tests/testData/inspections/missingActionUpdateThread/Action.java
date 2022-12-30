package com.sample;

import com.intellij.openapi.actionSystem.*;
import static com.intellij.openapi.actionSystem.ActionUpdateThread.*;

// plain
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionError</warning> extends AnAction {
  public void update(AnActionEvent e) { }
}

class ActionNoError extends AnAction {
  public void update(AnActionEvent e) { }

  public ActionUpdateThread getActionUpdateThread() { return EDT; }
}

// inherited
class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionChildError</warning> extends ActionError { }
class ActionChildNoError extends ActionNoError { }

// anonymous
class Holder {
  AnAction actionError = new <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionError</warning>() {
  };

  AnAction actionNoError = new ActionError() {
    public ActionUpdateThread getActionUpdateThread() { return EDT; }
  };
}

// default method (NOT applicable)
interface WithDefaultMethod extends ActionUpdateThreadAware {
  default ActionUpdateThread getActionUpdateThread() {
    return BGT;
  }
}

class <warning descr="Override 'getActionUpdateThread' and choose 'EDT' or 'BGT'">ActionErrorAnyway</warning> extends ActionError implements WithDefaultMethod {
}