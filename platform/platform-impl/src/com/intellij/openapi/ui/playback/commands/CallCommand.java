/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * Author: kirillk
 */
public class CallCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "call";

  public CallCommand(String text, int line) {
    super(text, line, true);
  }

  @Override
  protected ActionCallback _execute(final PlaybackContext context) {
    final ActionCallback cmdResult = new ActionCallback();

    final String cmd = getText().substring(PREFIX.length()).trim();
    final int open = cmd.indexOf("(");
    if (open == -1) {
      context.error("( expected", getLine());
      return ActionCallback.DONE;
    }

    final int close = cmd.lastIndexOf(")");
    if (close == -1) {
      context.error(") expected", getLine());
      return ActionCallback.DONE;
    }


    final String methodName = cmd.substring(0, open);
    String[] args = cmd.substring(open + 1, close).split(",");
    final boolean noArgs = args.length == 1 && args[0].length() == 0;
    Class[] types = noArgs ? new Class[1] : new Class[args.length + 1];
    types[0] = PlaybackContext.class;
    for (int i = 1; i < types.length; i++) {
      types[i] = String.class;
    }


    try {
      Pair<Method, Class> methodClass = findMethod(context, methodName, types);
      if (methodClass == null) {
        context.error("No method \"" + methodName + "\" found in facade classes: " + context.getCallClasses(), getLine());
        return ActionCallback.REJECTED;
      }

      Method m = methodClass.getFirst();

      if (!m.getReturnType().isAssignableFrom(AsyncResult.class)) {
        context.error("Method " + methodClass.getSecond() + ":" + methodName + " must return AsyncResult object", getLine());
        return ActionCallback.REJECTED;
      }

      Object[] actualArgs = noArgs ? new Object[1] : new Object[args.length + 1];
      actualArgs[0] = context;
      System.arraycopy(args, 0, actualArgs, 1, actualArgs.length - 1);


      AsyncResult result = (AsyncResult<String>)m.invoke(null, actualArgs);
      if (result == null) {
        context.error("Method " + methodClass.getSecond() + ":" + methodName + " must return AsyncResult object, but was null", getLine());
        return ActionCallback.REJECTED;
      }

      result.doWhenDone((Consumer<String>)s -> {
        if (s != null) {
          context.message(s, getLine());
        }
        cmdResult.setDone();
      }).doWhenRejected(s -> {
        context.error(s, getLine());
        cmdResult.setRejected();
      });
    }
    catch (InvocationTargetException ignored) {
      context.error("InvocationTargetException while executing command: " + cmd, getLine());
    }
    catch (IllegalAccessException ignored) {
      context.error("IllegalAccessException while executing command: " + cmd, getLine());
    }
    return cmdResult;
  }

  private static Pair<Method, Class> findMethod(PlaybackContext context, String methodName, Class[] types) {
    Set<Class> classes = context.getCallClasses();
    for (Class eachClass : classes) {
      try {
        Method method = eachClass.getMethod(methodName, types);
        return Pair.create(method, eachClass);
      }
      catch (NoSuchMethodException ignored) {
      }
    }

    return null;
  }
}
