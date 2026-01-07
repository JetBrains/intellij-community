// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public final class CommandSeparatorTest extends LightPlatformTestCase {

  private CommandSeparator separator;
  private List<State> separatorOutput;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    separatorOutput = new ArrayList<>();
    separator = createSeparator(separatorOutput);
  }

  public void testCC() {
    startCommand();
    finishCommand();
    assertResult(
      "separator must forward start-finish pair as is",
      State.COMMAND_STARTED,
      State.COMMAND_FINISHED
    );
  }

  public void testTT() {
    startTransparent();
    finishTransparent();
    assertResult(
      "separator must forward transparent start-finish pair as is",
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED
    );
  }

  public void testCTTC() {
    startCommand();
    startTransparent();
    finishTransparent();
    finishCommand();
    assertResult(
      "separator must drop nested transparent start-finish pair",
      State.COMMAND_STARTED,
      State.COMMAND_FINISHED
    );
  }

  public void testTCCT() {
    startTransparent();
    startCommand();
    finishCommand();
    finishTransparent();
    assertResult(
      "separator must drop nested start-finish pair",
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED
    );
  }

  public void testCTCT() {
    startCommand();
    startTransparent();
    finishCommand();
    finishTransparent();
    assertResult(
      "separator must linearize overlapping commands",
      State.COMMAND_STARTED,
      State.COMMAND_FINISHED,
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED
    );
  }

  public void testTCTC() {
    startTransparent();
    startCommand();
    finishTransparent();
    finishCommand();
    assertResult(
      "separator must linearize overlapping commands",
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED
    );
  }

  public void testCCCC() {
    startCommand();
    finishCommand();
    startCommand();
    finishCommand();
    assertResult(
      "separator must forward start-finish pair as is",
      State.COMMAND_STARTED,
      State.COMMAND_FINISHED,
      State.COMMAND_STARTED,
      State.COMMAND_FINISHED
    );
  }

  public void testTTTT() {
    startTransparent();
    finishTransparent();
    startTransparent();
    finishTransparent();
    assertResult(
      "separator must forward transparent start-finish pair as is",
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED,
      State.TRANSPARENT_STARTED,
      State.TRANSPARENT_FINISHED
    );
  }

  public void testEmpty() {
    assertResult("separator must be empty if no commands were added");
  }

  public void testNestedCommandFails() {
    startCommand();
    assertThrows(
      IllegalStateException.class,
      () -> startCommand()
    );
  }

  public void testNestedTransparentFails() {
    startTransparent();
    assertThrows(
      IllegalStateException.class,
      () -> startTransparent()
    );
  }

  public void testFinishBeforeStartFails() {
    assertThrows(
      IllegalStateException.class,
      () -> finishCommand()
    );
  }

  public void testTransparentFinishBeforeStartFails() {
    assertThrows(
      IllegalStateException.class,
      () -> finishTransparent()
    );
  }

  private void startCommand() {
    separator.commandStarted(createCommandEvent());
  }

  private void finishCommand() {
    separator.commandFinished(createCommandEvent());
  }

  private void startTransparent() {
    separator.undoTransparentActionStarted();
  }

  private void finishTransparent() {
    separator.undoTransparentActionFinished();
  }

  private void assertResult(String message, State... expected) {
    assertEquals(
      message,
      List.of(expected),
      separatorOutput
    );
    assertTrue(
      "start-finish pair must leave the separator in the initial state but actual: " + separator.toString(),
      separator.isInitialState()
    );
  }

  private @NotNull CommandEvent createCommandEvent() {
    return new CommandEvent(
      CommandProcessor.getInstance(),
      () -> {},
      "command",
      new Object(),
      getProject(),
      UndoConfirmationPolicy.DEFAULT,
      false,
      null
    );
  }

  private static @NotNull CommandSeparator createSeparator(List<State> output) {
    return new CommandSeparator(new SeparatedCommandListener() {
      @Override
      public void onCommandStarted(@NotNull CmdEvent cmdStartEvent) {
        output.add(cmdStartEvent.isTransparent() ? State.TRANSPARENT_STARTED : State.COMMAND_STARTED);
      }

      @Override
      public void onCommandFinished(@NotNull CmdEvent cmdFinishEvent) {
        output.add(cmdFinishEvent.isTransparent() ? State.TRANSPARENT_FINISHED : State.COMMAND_FINISHED);
      }

      @Override
      public void onCommandFakeFinished(@NotNull CmdEvent cmdFakeFinishEvent) {
      }
    });
  }

  private enum State {
    COMMAND_STARTED,
    COMMAND_FINISHED,
    TRANSPARENT_STARTED,
    TRANSPARENT_FINISHED,
  }
}
