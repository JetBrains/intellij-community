// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ValueContainersPropertyTest {

  public static final int COMMANDS_COUNT = 8097;
  /**
   * COMMANDS_COUNT and FILE_IDS_COUNT better be the same order of magnitude -- because we want to test with enough
   * duplicated fileIds -- but we also don't want too much duplicates.
   */
  public static final int FILE_IDS_COUNT = COMMANDS_COUNT * 3 / 2;

  @ParameterizedTest
  @MethodSource("valueContainerImplementationsToTest")
  void updatableValueContainer_IsEquivalentToEtalon(@NotNull UpdatableValueContainer<String> container) {

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, FILE_IDS_COUNT);

    for (Command<String> command : commands) {
      command.applyTo(container);
      command.applyTo(etalon);
    }

    assertContainerState(container, etalon, commands);
  }


  @Test
  void changeTrackingContainer_accumulatesAllChanges_AppliedToSnapshot_AndItself() {
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();
    UpdatableValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> snapshot);

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, FILE_IDS_COUNT);


    for (int i = 0; i < commands.size(); i++) {
      Command<String> command = commands.get(i);

      command.applyTo(etalon);

      //apply first 1/2 of the commands to snapshot, and second 1/2 to changeTrackingContainer
      if (i < commands.size() / 2) {
        command.applyTo(snapshot);
      }
      else {
        command.applyTo(changeTrackingContainer);
      }
    }

    assertContainerState(
      changeTrackingContainer,
      etalon,
      commands
    );
  }

  @Test
  void regression_ChangeTrackingContainer_correctlyTracks_DuplicatedEntryAddAndRemove() {
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();
    UpdatableValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> snapshot);

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    //RC: There was a but in ChangeTrackingValueContainer then pair of changes (add X) followed by (remove X)
    //    was coalesced to (nothing).
    //    It is incorrect if <snapshot> contains X -- correct coalescing is (remove X).

    List<Command<String>> commandsToSnapshot = List.of(
      new Command<>(new Entry<>(1, "1"), /* add: */ true)
    );
    List<Command<String>> commandsToTracking = List.of(
      new Command<>(new Entry<>(1, "1"), /* add: */ true),
      new Command<>(new Entry<>(1, "1"), /* add: */ false)
    );

    for (Command<String> command : commandsToSnapshot) {
      command.applyTo(etalon);
      command.applyTo(snapshot);
    }

    for (Command<String> command : commandsToTracking) {
      command.applyTo(etalon);
      command.applyTo(changeTrackingContainer);
    }

    List<Command<String>> commands = new ArrayList<>(commandsToSnapshot);
    commands.addAll(commandsToTracking);

    assertContainerState(
      changeTrackingContainer,
      etalon,
      commands
    );
  }





  /* ======================================= infrastructure ==================================================== */

  private static Stream<Arguments> valueContainerImplementationsToTest() {
    return Stream.of(
      Arguments.of(new ValueContainerImpl<>()),
      Arguments.of(new ChangeTrackingValueContainer<>(ValueContainerImpl::new))
      //Arguments.of(new TransientChangeTrackingValueContainer<>(ValueContainerImpl::new) ),
    );
  }

  private static <V> void assertContainerState(@NotNull ValueContainer<V> actualContainer,
                                               @NotNull IdealValueContainer<V> etalon,
                                               @NotNull Collection<Command<V>> commands) {
    if (actualContainer.size() != etalon.inputIdToValue.size()) {
      throw new AssertionError(
        "Mismatch:\n" +

        "actual (" + actualContainer.getClass().getName() + "): \n" +
        new IdealValueContainer<>(actualContainer).inputIdToValue + "\n" +

        "vs expected: \n" +
        etalon.inputIdToValue + "\n" +

        "commands applied: \n" + commands.stream().map(Command::toString).collect(joining("\n"))
      );
    }

    actualContainer.forEach((id, value) -> {
      V expectedValue = etalon.inputIdToValue.get(id);
      assertEquals(
        expectedValue,
        value,
        "Value for id(=" + id + ") is different\n" +
        "commands applied: \n" +
        commands.stream().map(Command::toString).collect(joining("\n"))
      );
      return true;
    });
  }

  @SuppressWarnings("SameParameterValue")
  private static @NotNull List<Command<String>> randomCommands(int commandsCount,
                                                               int fileIdsCount) {
    List<Command<String>> commands = randomCommands(fileIdsCount)
      .limit(commandsCount)
      .collect(toList());
    Collections.shuffle(commands);
    return commands;
  }


  private static Stream<Command<String>> randomCommands(int potentialFileIdsCount) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return rnd.ints(0, potentialFileIdsCount)
      .mapToObj(inputId -> new Entry<>(inputId, String.valueOf(inputId)))
      .flatMap(entry -> {
        int dice = rnd.nextInt(3);
        return switch (dice) {
          case 0 -> Stream.of(new Command<>(entry, /*add: */true));
          case 1 -> Stream.of(new Command<>(entry, /*add: */true), new Command<>(entry, /*add: */false));
          default -> Stream.of(new Command<>(entry, /*add: */false));
        };
      });
  }

  record Command<V>(Entry<V> entry, boolean add) {
    public void applyTo(@NotNull UpdatableValueContainer<V> container) {
      if (add) {
        container.addValue(entry.inputId, entry.value);
      }
      else {
        container.removeAssociatedValue(entry.inputId);
      }
    }

    public void applyTo(@NotNull IdealValueContainer<V> container) {
      if (add) {
        container.addValue(entry.inputId, entry.value);
      }
      else {
        container.removeAssociatedValue(entry.inputId);
      }
    }

    @Override
    public String toString() {
      if (add) {
        return "added   (" + entry.inputId + " -> '" + entry.value + "')";
      }
      else {
        return "removed (" + entry.inputId + " -> '" + entry.value + "')";
      }
    }
  }

  record Entry<V>(int inputId, @NotNull V value) {
  }

  static class IdealValueContainer<V> {
    private final Int2ObjectOpenHashMap<V> inputIdToValue = new Int2ObjectOpenHashMap<>();

    IdealValueContainer() {
    }

    IdealValueContainer(@NotNull ValueContainer<? extends V> container) {
      container.forEach((inputId, value) -> {
        addValue(inputId, value);
        return true;
      });
    }

    IdealValueContainer(Entry<V>[] entries) {
      for (Entry<V> entry : entries) {
        addValue(entry.inputId, entry.value);
      }
    }


    public V addValue(int inputId, V value) {
      return inputIdToValue.put(inputId, value);
    }

    public V removeAssociatedValue(int inputId) {
      return inputIdToValue.remove(inputId);
    }

    @Override
    public String toString() {
      return "IdealValueContainer: " + inputIdToValue;
    }
  }
}