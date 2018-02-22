// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.playback.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.Pair;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

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
  protected Promise<Object> _execute(final PlaybackContext context) {
    final String cmd = getText().substring(PREFIX.length()).trim();
    final int open = cmd.indexOf("(");
    if (open == -1) {
      context.error("( expected", getLine());
      return Promises.resolvedPromise();
    }

    final int close = cmd.lastIndexOf(")");
    if (close == -1) {
      context.error(") expected", getLine());
      return Promises.resolvedPromise();
    }

    final String methodName = cmd.substring(0, open);
    String[] args = cmd.substring(open + 1, close).split(",");
    final boolean noArgs = args.length == 1 && args[0].length() == 0;
    Class[] types = noArgs ? new Class[1] : new Class[args.length + 1];
    types[0] = PlaybackContext.class;
    for (int i = 1; i < types.length; i++) {
      types[i] = String.class;
    }


    final AsyncPromise<Object> cmdResult = new AsyncPromise<>();
    try {
      Pair<Method, Class> methodClass = findMethod(context, methodName, types);
      if (methodClass == null) {
        context.error("No method \"" + methodName + "\" found in facade classes: " + context.getCallClasses(), getLine());
        return Promises.rejectedPromise();
      }

      Method m = methodClass.getFirst();

      if (!m.getReturnType().isAssignableFrom(Promise.class)) {
        context.error("Method " + methodClass.getSecond() + ":" + methodName + " must return Promise object", getLine());
        return Promises.rejectedPromise();
      }

      Object[] actualArgs = noArgs ? new Object[1] : new Object[args.length + 1];
      actualArgs[0] = context;
      System.arraycopy(args, 0, actualArgs, 1, actualArgs.length - 1);


      Promise<String> result = (Promise<String>)m.invoke(null, actualArgs);
      if (result == null) {
        context.error("Method " + methodClass.getSecond() + ":" + methodName + " must return AsyncResult object, but was null", getLine());
        return Promises.rejectedPromise();
      }

      result
        .onSuccess(s -> {
          if (s != null) {
            context.message(s, getLine());
          }
          cmdResult.setResult(null);
        })
        .onError(error -> {
          context.error(error.getMessage(), getLine());
          cmdResult.setError(error);
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
