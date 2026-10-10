// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.codeInspection.options.OptControl;
import com.intellij.codeInspection.options.OptCustom;
import com.intellij.codeInspection.options.OptDropdown;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptRegularComponent;
import com.intellij.codeInspection.options.OptionContainer;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A question of a refactoring whose answer changes the result for the given targets. The questions are asked in the vocabulary of
 * {@link ModCommand}, see {@link #askCommand}: the IDE shows them in dialogs, batch execution takes the initial values, and a client such
 * as LSP reads them the same way.
 */
public sealed interface ModPlanQuestion {
  /**
   * An interpreter does not ask an answered question again.
   *
   * @param currentValues the answers so far
   * @return whether {@code currentValues} answer this question
   */
  boolean isAnswered(@NotNull ModPlanOptionValues currentValues);

  /**
   * @return where a UI remembers the answers between runs; null if they are not remembered, such as for a custom question
   */
  default @Nullable Storage storage() {
    return null;
  }

  /**
   * Asks {@code question} in a dialog. The pane of a custom question holds one {@link OptCustom} with the id {@link Custom#COMPONENT_ID},
   * and the IDE shows the UI of the question for it. Batch execution, and a client that cannot show the custom component, take
   * {@code initialValues}.
   *
   * @param title         the title of the dialog
   * @param initialValues the answers that are already known; they are the initial values of the dialog
   * @param nextCommand   builds the command from the answers
   * @return a command that asks the question, and runs the command {@code nextCommand} builds from the answers
   */
  static @NotNull ModCommand askCommand(@NlsContexts.DialogTitle @NotNull String title,
                                        @NotNull ModPlanQuestion question,
                                        @NotNull ModPlanOptionValues initialValues,
                                        @NotNull Function<? super ModPlanOptionValues, ? extends ModCommand> nextCommand) {
    return switch (question) {
      case Form form -> askForm(title, form, initialValues, nextCommand);
      case Custom customQuestion -> askCustom(title, customQuestion, initialValues, nextCommand);
    };
  }

  private static @NotNull ModCommand askForm(@NlsContexts.DialogTitle @NotNull String title,
                                             @NotNull Form form,
                                             @NotNull ModPlanOptionValues currentValues,
                                             @NotNull Function<? super ModPlanOptionValues, ? extends ModCommand> nextCommand) {
    return new ModEditOptions<>(title, () -> new Form.Answers(form, currentValues), true, answers -> nextCommand.apply(answers.myValues));
  }

  private static @NotNull ModCommand askCustom(@NlsContexts.DialogTitle @NotNull String title,
                                       @NotNull Custom question,
                                       @NotNull ModPlanOptionValues currentValues,
                                       @NotNull Function<? super ModPlanOptionValues, ? extends ModCommand> nextCommand) {
    return new ModEditOptions<>(title, () -> customAnswers(question, currentValues), true,
                                answers -> nextCommand.apply((ModPlanOptionValues)answers.getOptionController().getOption(Custom.ANSWERS_BIND_ID)));
  }

  /** @return the options of the dialog of {@code question}: the question itself, and the answers so far, which its UI replaces */
  private static @NotNull OptionContainer customAnswers(@NotNull Custom question, @NotNull ModPlanOptionValues currentValues) {
    return new OptionContainer() {
      private @NotNull ModPlanOptionValues myValues = currentValues;

      @Override
      public @NotNull OptPane getOptionsPane() {
        return OptPane.pane(new OptCustom(Custom.COMPONENT_ID));
      }

      @Override
      public @NotNull OptionController getOptionController() {
        return OptionController.of(this::getValue, this::setValue);
      }

      private @NotNull Object getValue(@NotNull String bindId) {
        return switch (bindId) {
          case Custom.QUESTION_BIND_ID -> question;
          case Custom.ANSWERS_BIND_ID -> myValues;
          default -> throw new IllegalArgumentException("Unknown option: " + bindId);
        };
      }

      private void setValue(@NotNull String bindId, Object value) {
        if (!Custom.ANSWERS_BIND_ID.equals(bindId) || !(value instanceof ModPlanOptionValues answers)) {
          throw new IllegalArgumentException("Unknown option: " + bindId);
        }
        myValues = answers;
      }
    };
  }

  /**
   * Options asked with a pane, see {@link #askCommand}. The dialog shows the pane as it is, so one form asks several options together.
   * {@link #of} builds the form of a list of options; a language lays out a larger pane itself: groups, tabs, the controls that a checkbox
   * enables.
   * <p>
   * The pane binds to the options by id: each control of {@code pane} has the {@linkplain ModPlanOption#id id} of one of {@code options} as
   * its bindId, and each option has one control. The values stay in {@link ModPlanOptionValues}, so a headless caller sets them up front
   * by id.
   *
   * @param pane    the controls of the options; only the controls of {@link OptPane} with no Swing UI of their own work for every client
   * @param options the options that the pane asks
   * @param storage where the answers are remembered for the next run; null if they are not. A UI that can remember starts with
   *                {@link Storage#load} and calls {@link Storage#store} with the answers after OK; the headless callers ignore it
   */
  record Form(@NotNull OptPane pane, @NotNull @Unmodifiable List<ModPlanOption<?>> options, @Nullable Storage storage)
    implements ModPlanQuestion {
    public Form {
      options = List.copyOf(options);
      Set<String> optionIds = options.stream().map(ModPlanOption::getId).collect(Collectors.toSet());
      Set<String> bindIds = pane.allControls().stream().map(OptControl::bindId).collect(Collectors.toSet());
      if (!optionIds.equals(bindIds)) {
        throw new IllegalArgumentException("The controls of the pane must bind the options one to one: " + bindIds + " for " + optionIds);
      }
    }

    /**
     * @param options the options to ask, each with the control of {@link #control}, one under another in their order
     * @param storage see {@link #storage}
     * @return one form of all {@code options}: they are asked and answered together
     */
    public static @NotNull Form of(@NotNull List<ModPlanOption<?>> options, @Nullable Storage storage) {
      OptPane pane = OptPane.pane(ContainerUtil.map(options, Form::control).toArray(OptRegularComponent[]::new));
      return new Form(pane, options, storage);
    }

    /**
     * @return the control of {@code option}: it binds to the id of the option and shows its description. A boolean option is a checkbox, a
     * text option an edit box, an enum option a dropdown
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @NotNull OptRegularComponent control(@NotNull ModPlanOption<?> option) {
      String label = option.getDescription().get();
      if (option.getType() == Boolean.class) {
        return OptPane.checkbox(option.getId(), label);
      }
      if (option.getType() == String.class) {
        return OptPane.string(option.getId(), label);
      }
      return dropdown((ModPlanOption)option, label);
    }

    private static <E extends Enum<E>> @NotNull OptDropdown dropdown(@NotNull ModPlanOption<E> option, @Nls @NotNull String label) {
      Function<? super E, String> text = Objects.requireNonNull(option.getValueText());
      return OptPane.dropdown(option.getId(), label, option.getType(), text::apply);
    }

    /** @return whether all {@link #options} have explicit values in {@code currentValues}, such as answers earlier in the run */
    @Override
    public boolean isAnswered(@NotNull ModPlanOptionValues currentValues) {
      return ContainerUtil.and(options, currentValues::has);
    }

    /** The options of the dialog of a form: the controls of its pane bind to the ids of the options. */
    private static final class Answers implements OptionContainer {
      private final @NotNull OptPane myPane;
      private final @NotNull Map<String, ModPlanOption<?>> myOptionsById = new LinkedHashMap<>();
      private @NotNull ModPlanOptionValues myValues;

      private Answers(@NotNull Form form, @NotNull ModPlanOptionValues initialValues) {
        myPane = form.pane();
        for (ModPlanOption<?> option : form.options()) {
          myOptionsById.put(option.getId(), option);
        }
        myValues = initialValues;
      }

      @Override
      public @NotNull OptPane getOptionsPane() {
        return myPane;
      }

      @Override
      public @NotNull OptionController getOptionController() {
        return OptionController.of(this::getAnswer, this::setAnswer);
      }

      private Object getAnswer(@NotNull String id) {
        return myValues.get(optionOf(id));
      }

      private void setAnswer(@NotNull String id, Object value) {
        myValues = with(myValues, optionOf(id), value);
      }

      private @NotNull ModPlanOption<?> optionOf(@NotNull String id) {
        ModPlanOption<?> option = myOptionsById.get(id);
        if (option == null) {
          throw new IllegalArgumentException("Unknown option: " + id);
        }
        return option;
      }

      private static <T> @NotNull ModPlanOptionValues with(@NotNull ModPlanOptionValues optionValues, @NotNull ModPlanOption<T> option, Object value) {
        T typedValue = option.fromRaw(value);
        return typedValue == null ? optionValues : optionValues.with(option, typedValue);
      }
    }
  }

  /**
   * Where a UI keeps the answers to {@link Form} questions between runs, for example in the settings of the refactoring, see
   * {@link ModPlanQuestion#storage()}.
   */
  interface Storage {
    /**
     * @return {@code optionValues} with the remembered values of the options that this storage keeps
     */
    @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues optionValues);

    /**
     * Remembers the values of the options that this storage keeps.
     *
     * @param answers the answers of the user, after OK
     */
    void store(@NotNull ModPlanOptionValues answers);
  }

  /**
   * A question that a language asks with its own UI. The answers are values of {@link #options()}: a headless caller sets them
   * up front instead.
   * <p>
   * <b>Do not keep a live PSI element in a question.</b> A question is created in a read action of a {@link ModPlanChain} and asked later
   * on the EDT, after the read action ends. A write action can change PSI while the dialog is open. A {@code ModCommand} can keep the
   * question long after the run. So an element that the question keeps can become invalid, or keep a whole file in memory.
   * <ul>
   *   <li>Keep the data of the question: the texts, the names and the keys that the dialog shows and the answer holds.</li>
   *   <li>Keep an element only as a {@link SymbolPointer} or a {@link com.intellij.psi.SmartPsiElementPointer}, and get the
   *   element again in {@link #ask}. It can be gone: then {@link #ask} returns null.</li>
   *   <li>This also applies to a lambda or an inner class of the question, and to the objects that they capture, such as a
   *   {@code UsageInfo} or a {@code PsiMethod}.</li>
   * </ul>
   */
  non-sealed interface Custom extends ModPlanQuestion {
    /** The id of the {@link OptCustom} that stands for a custom question in {@link ModPlanQuestion#askCommand}. */
    @ApiStatus.Internal
    @NonNls String COMPONENT_ID = "modPlan.question";

    /** The bindId of the question in the dialog of a custom question: its value is the {@link Custom} itself. */
    @ApiStatus.Internal
    @NonNls String QUESTION_BIND_ID = "question";

    /**
     * The bindId of the answers in the dialog of a custom question: its value is {@link ModPlanOptionValues}, and the answer sets it whole.
     */
    @ApiStatus.Internal
    @NonNls String ANSWERS_BIND_ID = "answers";

    /**
     * @return the options this question answers
     */
    @NotNull @Unmodifiable List<ModPlanOption<?>> options();

    /**
     * Override it for a question whose options other questions answer too, such as the subset of the overriding methods of one method in
     * a list of all the chosen ones.
     *
     * @return by default, whether the question has options and all of them have explicit values
     */
    @Override
    default boolean isAnswered(@NotNull ModPlanOptionValues currentValues) {
      List<ModPlanOption<?>> answeredOptions = options();
      return !answeredOptions.isEmpty() && ContainerUtil.and(answeredOptions, currentValues::has);
    }

    /**
     * Shows the UI of the question.
     *
     * @param currentValues the answers known so far
     * @return {@code currentValues} with the answers to {@link #options()}; null if the user cancelled the refactoring
     */
    @RequiresEdt
    @Nullable ModPlanOptionValues ask(@NotNull Project project, @NotNull ModPlanOptionValues currentValues);
  }
}
