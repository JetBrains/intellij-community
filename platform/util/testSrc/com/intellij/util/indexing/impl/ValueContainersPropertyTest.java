// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.impl;

import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.io.DataOutputStream;
import com.intellij.util.io.EnumeratorStringDescriptor;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test suits compares behavior of various {@link ValueContainer} implementations in various scenarios, to the
 * behavior of a reference implementation, {@link IdealValueContainer}.
 */
class ValueContainersPropertyTest {

  public static final int COMMANDS_COUNT = 8097;

  /**
   * COMMANDS_COUNT and MAX_FILE_ID better be the same order of magnitude -- because we want enough duplicated
   * fileIds in test data -- but we also don't want too much duplicates, because this is unrealistic.
   */
  public static final int MAX_FILE_ID = COMMANDS_COUNT * 3 / 2;

  @ParameterizedTest
  @MethodSource("valueContainerImplementationsToTest")
  void updatableValueContainer_withCommandsApplied_IsEquivalentToEtalon(@NotNull UpdatableValueContainer<String> containerImpl) {

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, MAX_FILE_ID);

    for (Command<String> command : commands) {
      command.applyTo(containerImpl);
      command.applyTo(etalon);
    }

    assertContainerStatesMatch(containerImpl, etalon, commands);
  }

  @ParameterizedTest
  @MethodSource("valueContainerImplementationsToTest")
  void updatableValueContainer_withCommandsApplied_serializedAndDeserialized_IsEquivalentToEtalon(@NotNull UpdatableValueContainer<String> containerImpl)
    throws IOException {

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, MAX_FILE_ID);

    for (Command<String> command : commands) {
      command.applyTo(containerImpl);
      command.applyTo(etalon);
    }

    ValueContainer<String> deserializedContainer = serializeAndDeserializeFully(containerImpl);

    assertContainerStatesMatch(
      deserializedContainer,
      etalon,
      commands
    );
  }


  @Test
  void changeTrackingContainer_accumulatesAllChanges_AppliedToSnapshot_AndItself() {
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();
    UpdatableValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> snapshot);

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, MAX_FILE_ID);


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

    assertContainerStatesMatch(
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

    assertContainerStatesMatch(
      changeTrackingContainer,
      etalon,
      commands
    );
  }


  @Test
  void changeTrackingContainer_serializedAndDeserializedFully_IsEquivalentToTheEtalon() throws IOException {
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();
    UpdatableValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> snapshot);

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, MAX_FILE_ID);


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

    ValueContainer<String> deserializedContainer = serializeAndDeserializeFully(changeTrackingContainer);

    assertContainerStatesMatch(
      deserializedContainer,
      etalon,
      commands
    );
  }


  @Test
  void changeTrackingContainer_serializedAndDeserializedWithDiff_IsEquivalentToTheEtalon() throws IOException {
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();
    ChangeTrackingValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> snapshot);

    IdealValueContainer<String> etalon = new IdealValueContainer<>();

    List<Command<String>> commands = randomCommands(COMMANDS_COUNT, MAX_FILE_ID);


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

    ValueContainer<String> deserializedContainer = serializeAndDeserializeWithDiff(snapshot, changeTrackingContainer);

    assertContainerStatesMatch(
      deserializedContainer,
      etalon,
      commands
    );
  }


  @Test
  void changeTrackingContainer_serializedAndDeserializedWithDiff_ManyTimes_IsEquivalentToTheEtalon() throws IOException {

    IdealValueContainer<String> etalon = new IdealValueContainer<>();
    List<Command<String>> totalCommands = new ArrayList<>();
    ValueContainerImpl<String> snapshot = new ValueContainerImpl<>();

    int turns = 8;

    for (int j = 0; j < turns; j++) {
      var _snapshot = snapshot;//effectively-final, for lambda to capture
      ChangeTrackingValueContainer<String> changeTrackingContainer = new ChangeTrackingValueContainer<>(() -> _snapshot);

      List<Command<String>> commands = randomCommands(COMMANDS_COUNT / turns, MAX_FILE_ID);

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
      totalCommands.addAll(commands);

      snapshot = serializeAndDeserializeWithDiff(snapshot, changeTrackingContainer);
    }

    assertContainerStatesMatch(
      snapshot,
      etalon,
      totalCommands
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


  private static ValueContainerImpl<String> serializeAndDeserializeFully(
    @NotNull UpdatableValueContainer<String> changeTrackingContainer) throws IOException {
    EnumeratorStringDescriptor externalizer = EnumeratorStringDescriptor.INSTANCE;

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(bos)) {
      changeTrackingContainer.saveTo(dos, externalizer);
    }

    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
    ValueContainerImpl<String> container = ValueContainerImpl.createNewValueContainer();
    container.readFrom(
      new DataInputStream(bis),
      externalizer,
      ValueContainerInputRemapping.IDENTITY
    );
    return container;
  }

  private static @NotNull ValueContainerImpl<String> serializeAndDeserializeWithDiff(
    @NotNull ValueContainerImpl<String> snapshot,
    @NotNull ChangeTrackingValueContainer<String> changeTrackingContainer) throws IOException {

    EnumeratorStringDescriptor externalizer = EnumeratorStringDescriptor.INSTANCE;

    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (DataOutputStream dos = new DataOutputStream(bos)) {
      snapshot.saveTo(dos, externalizer);
      changeTrackingContainer.size();
      changeTrackingContainer.saveDiffTo(dos, externalizer);
    }

    ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
    ValueContainerImpl<String> deserializedContainer = ValueContainerImpl.createNewValueContainer();
    deserializedContainer.readFrom(
      new DataInputStream(bis),
      externalizer,
      ValueContainerInputRemapping.IDENTITY
    );
    return deserializedContainer;
  }

  /**
   * Asserts that state of actualContainer matches the state of etalon container.
   *
   * @param commands commands applied to both containers; they are used to format an error message, in case of mismatch
   *                 -- so, basically, it is an error message in disguise.
   */
  private static <V> void assertContainerStatesMatch(@NotNull ValueContainer<V> actualContainer,
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


  private static Stream<Command<String>> randomCommands(int maxFileId) {
    ThreadLocalRandom rnd = ThreadLocalRandom.current();
    return rnd.ints(1, maxFileId)
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

  /**
   * Reference implementation of {@link ValueContainer}. Naturally it should be an {@link ValueContainer} subclass,
   * but {@link ValueContainer} has many methods that are not needed here. We need a reference implementation to
   * be very simple, and capture the very essence of a value container -- the only thing needed is an ability to
   * compare its accumulated state against some {@link ValueContainer} impl. Hence, this class doesn't extend
   * {@link ValueContainer}, but rather is a very stripped down version of it.
   */
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