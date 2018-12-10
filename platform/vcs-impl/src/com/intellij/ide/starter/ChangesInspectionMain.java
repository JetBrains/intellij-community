// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.starter;

import com.intellij.codeInspection.InspectionToolCmdlineOptionHelpProvider;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.openapi.application.ApplicationStarter;

import java.util.Arrays;

public class ChangesInspectionMain implements ApplicationStarter {
  private ChangesInspectionApplication myApplication;

  @Override
  public String getCommandName() {
    return "changes-inspect";
  }

  @Override
  @SuppressWarnings({"HardCodedStringLiteral"})
  public void premain(String[] args) {
    if (args.length < 2) {
      System.err.println("invalid args:" + Arrays.toString(args));
      printHelp();
    }

    //System.setProperty("idea.load.plugins.category", "inspection");
    myApplication = new ChangesInspectionApplication();

    myApplication.myHelpProvider = new InspectionToolCmdlineOptionHelpProvider() {
      @Override
      public void printHelpAndExit() {
        printHelp();
      }
    };
    myApplication.myProjectPath = args[1];
  }

  @Override
  public void main(String[] args) {
    myApplication.startup();
  }

  public static void printHelp() {
    System.out.println(InspectionsBundle.message("inspection.command.line.explanation"));
    System.exit(1);
  }
}

