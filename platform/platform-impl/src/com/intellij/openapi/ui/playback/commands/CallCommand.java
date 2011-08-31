/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.ui.playback.PlaybackCallFacade;
import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by IntelliJ IDEA.
 * User: kirillk
 * Date: 8/3/11
 * Time: 4:08 PM
 * To change this template use File | Settings | File Templates.
 */
public class CallCommand extends AbstractCommand {

  public static final String PREFIX = CMD_PREFIX + "call";

  public CallCommand(String text, int line) {
    super(text, line);
  }

  @Override
  protected ActionCallback _execute(final PlaybackContext context) {
    final ActionCallback cmdResult = new ActionCallback();

    final String cmd = getText().substring(PREFIX.length()).trim();
    final int open = cmd.indexOf("(");
    if (open == -1) {
      context.getCallback().error("( expected", getLine());
      return new ActionCallback.Done();
    }

    final int close = cmd.lastIndexOf(")");
    if (close == -1) {
      context.getCallback().error(") expected", getLine());
      return new ActionCallback.Done();
    }


    final String methodName = cmd.substring(0, open);
    String [] args = cmd.substring(open + 1, close).split(",");
    final boolean noArgs = args.length == 1 && args[0].length() == 0;
    Class[] types = noArgs ? new Class[1] : new Class[args.length + 1];
    types[0] = PlaybackContext.class;
    for (int i = 1; i < types.length; i++) {
      types[i] = String.class;
    }

    
    try {
     final Method m = PlaybackCallFacade.class.getMethod(methodName, types);
     if (!m.getReturnType().isAssignableFrom(AsyncResult.class)) {
       context.getCallback().error("Method " + methodName + " must return AsyncResult object", getLine());
       return new ActionCallback.Rejected();
     }
     
     Object[] actualArgs = noArgs ? new Object[1] : new Object[args.length + 1];
     actualArgs[0] = context;
      for (int i = 1; i < actualArgs.length; i++) {
        actualArgs[i] = args[i - 1];
      }


     AsyncResult result = (AsyncResult<String>)m.invoke(null, actualArgs);
     if (result == null) {
       context.getCallback().error("Method " + methodName + " must return AsyncResult object, but was null", getLine());
       return new ActionCallback.Done();
     }
      
     result.doWhenDone(new AsyncResult.Handler<String>() {
       @Override
       public void run(String s) {
         if (s != null) {
           context.getCallback().message(s, getLine());
         }
         cmdResult.setDone();
       }
     }).doWhenRejected(new AsyncResult.Handler<String>() {
       @Override
       public void run(String s) {
         context.getCallback().error(s, getLine());
         cmdResult.setRejected();
       }
     }); 
      
    }
    catch (NoSuchMethodException e) {
      context.getCallback().error("No method found in PlaybackCallFacade", getLine());
    }
    catch (InvocationTargetException e) {
      context.getCallback().error("InvocationTargetException while executing command: " + cmd, getLine());
    }
    catch (IllegalAccessException e) {
      context.getCallback().error("IllegalAccessException while executing command: " + cmd, getLine());
    }
    return cmdResult;
  }

  @Override
  protected boolean isAwtThread() {
    return true;
  }
}
