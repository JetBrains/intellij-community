// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;


/**
 * Runtime invariants checking facilities. Provides the simplest form of the
 * <a href="https://en.wikipedia.org/wiki/Design_by_contract">contract programming</a>.
 *
 * <p>There are three groups of methods:
 * <ol>
 *  <li><i>Preconditions</i> (input checks) via {@code require} methods. Those methods should be used to
 *  check system arguments and other input data.
 *  </li>
 *  <li><i>Invariants</i> (state checks) via {@code check} methods. Those methods should be used to
 *  check system internal state or some predicates which are expected to be satisfied at the call site.
 *  </li>
 *  <li><i>Postconditions</i> (output checks) via {@code ensure} methods. Those methods should be used to
 *  check system results and other output data.
 *  </li>
 * </ol>
 * </p>
 *
 * <p>Each method contains counterpart with {@code andLog} suffix -- those methods only log errors
 * and do not throw exception, i.e do not break the control flow.
 * We suggest using overloads with {@link Attachment} provided, it may significantly reduce error investigation time.</p>
 *
 * <p>Usage example:
 * <pre>{@code
 * private @NotNull Result process(int index, int value) {
 *   // critical, will throw if index is wrong
 *   Checks.checkIndex(index, myRegistrar.getSize());
 *   // non-critical, will log and continue
 *   Checks.requireAndLog(
 *     myRegistrar.contains(value), () -> buildErrorInfo(value));
 *
 *   // ...
 *   // doing some work
 *   // ...
 *   // check and attach some info if it fails
 *   Checks.checkAndLog(
 *     MyProcessor.class,
 *     allInvariantsHoldFor(myRegistrar),
 *     "Registrar is in inconsistent state",
 *     AttachmentFactory.createAttachment(myFile));
 *
 *   // ...
 *   if      (isVariant1()) { ... }
 *   else if (isVariant2()) { ... }
 *   else if (isVariant3()) { ... }
 *   else {
 *     // can not tell the compiler that this branch is impossible
 *     Checks.unreachable();
 *   }
 *
 *   // ...
 *   // finishing work
 *
 *   // checking the result validity
 *   Checks.ensure(isValid(result));
 * }
 * }</pre>
 * </p>
 *
 * <p><b>NOTE:</b> for performance critical parts of code of for the checks
 * which should not be enabled under some circumstances it is likely better to use standard Java assertions
 * via {@code assert statement : message} construction.</p>
 *
 * @deprecated use Kotlin with its {@link kotlin.PreconditionsKt#check}/{@link kotlin.PreconditionsKt#require} functions
 */
@Deprecated
@ApiStatus.Experimental
@ApiStatus.Internal
public final class Checks {

  public static final @NotNull @NonNls String PRECONDITION_IS_NOT_SATISFIED = "Precondition is not satisfied";
  public static final @NotNull @NonNls String INVARIANT_IS_NOT_SATISFIED = "Invariant is not satisfied";
  public static final @NotNull @NonNls String POSTCONDITION_IS_NOT_SATISFIED = "Postcondition is not satisfied";

  private Checks() {
  }

  /* ------------------------------------------------------------------------------------------- */
  //region Preconditions, input assertions

  /**
   * @throws IllegalArgumentException if the {@code statement} is {@code false}
   */
  @Contract("false -> fail")
  public static void require(boolean statement) {
    if (!statement) {
      throw new IllegalArgumentException(PRECONDITION_IS_NOT_SATISFIED);
    }
  }

  /**
   * @throws IllegalArgumentException with the result of calling {@code lazyMessage}
   *                                  if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void require(boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      throw new IllegalArgumentException(lazyMessage.get().toString());
    }
  }

  /**
   * @throws IllegalArgumentException with the given {@code message} if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void require(boolean statement, @NotNull String message) {
    if (!statement) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * @throws IllegalArgumentException with the given {@code message}
   *                                  if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void require(@NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalArgumentException(message);
    }
  }

  /**
   * @throws IllegalArgumentException with the result of calling {@code lazyMessage}
   *                                  if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void require(@NotNull Supplier<Object> lazyMessage, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalArgumentException(lazyMessage.get().toString());
    }
  }

  /**
   * Logs {@link IllegalArgumentException} if the {@code statement} is {@code false}
   */
  public static void requireAndLog(@NotNull Class<?> loggingClass, boolean statement) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalArgumentException(PRECONDITION_IS_NOT_SATISFIED));
    }
  }

  /**
   * Logs {@link IllegalArgumentException} with the result of calling {@code lazyMessage}
   * if the {@code statement} is {@code false}.
   */
  public static void requireAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalArgumentException(lazyMessage.get().toString()));
    }
  }

  /**
   * Logs {@link IllegalArgumentException} with the given {@code message} if the {@code statement} is {@code false}.
   */
  public static void requireAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull String message) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalArgumentException(message));
    }
  }

  /**
   * Logs {@link IllegalArgumentException} with the given {@code message} and {@code attachments} if the {@code statement} is {@code false}.
   */
  public static void requireAndLog(
    @NotNull Class<?> loggingClass,
    boolean statement,
    @NotNull String message,
    Attachment @NotNull ... attachments
  ) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(message, new IllegalArgumentException(message), attachments);
    }
  }

  /**
   * Logs {@link IllegalArgumentException} with the given {@code message}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void requireAndLog(@NotNull Class<?> loggingClass, @NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalArgumentException(message));
    }
  }

  /**
   * Logs {@link IllegalArgumentException} with the result of calling {@code lazyMessage}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void requireAndLog(
    @NotNull Class<?> loggingClass,
    @NotNull Supplier<Object> lazyMessage,
    @NotNull BooleanSupplier statementSupplier
  ) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalArgumentException(lazyMessage.get().toString()));
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region Invariants, state assertions

  /**
   * @throws IllegalStateException if the {@code statement} is {@code false}
   */
  @Contract("false -> fail")
  public static void check(boolean statement) {
    if (!statement) {
      throw new IllegalStateException(INVARIANT_IS_NOT_SATISFIED);
    }
  }

  /**
   * @throws IllegalStateException with the result of calling {@code lazyMessage}
   *                               if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void check(boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      throw new IllegalStateException(lazyMessage.get().toString());
    }
  }

  /**
   * @throws IllegalStateException with the given {@code message} if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void check(boolean statement, @NotNull String message) {
    if (!statement) {
      throw new IllegalStateException(message);
    }
  }

  /**
   * @throws IllegalStateException with the given {@code message}
   *                               if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void check(@NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalStateException(message);
    }
  }

  /**
   * @throws IllegalStateException with the result of calling {@code lazyMessage}
   *                               if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void check(@NotNull Supplier<Object> lazyMessage, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalStateException(lazyMessage.get().toString());
    }
  }

  /**
   * Logs {@link IllegalStateException} if the {@code statement} is {@code false}
   */
  public static void checkAndLog(@NotNull Class<?> loggingClass, boolean statement) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalStateException(INVARIANT_IS_NOT_SATISFIED));
    }
  }

  /**
   * Logs {@link IllegalStateException} with the result of calling {@code lazyMessage}
   * if the {@code statement} is {@code false}.
   */
  public static void checkAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalStateException(lazyMessage.get().toString()));
    }
  }

  /**
   * Logs {@link IllegalStateException} with the given {@code message} if the {@code statement} is {@code false}.
   */
  public static void checkAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull String message) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalStateException(message));
    }
  }


  /**
   * Logs {@link IllegalStateException} with the given {@code message} and {@code attachments} if the {@code statement} is {@code false}.
   */
  public static void checkAndLog(
    @NotNull Class<?> loggingClass,
    boolean statement,
    @NotNull String message,
    Attachment @NotNull ... attachments
  ) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(message, new IllegalStateException(message), attachments);
    }
  }

  /**
   * Logs {@link IllegalStateException} with the given {@code message}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void checkAndLog(@NotNull Class<?> loggingClass, @NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalStateException(message));
    }
  }

  /**
   * Logs {@link IllegalStateException} with the result of calling {@code lazyMessage}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void checkAndLog(
    @NotNull Class<?> loggingClass,
    @NotNull Supplier<Object> lazyMessage,
    @NotNull BooleanSupplier statementSupplier
  ) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalStateException(lazyMessage.get().toString()));
    }
  }


  /**
   * Ensures that {@code index} specifies a valid <i>element</i> in an array, collection or string of size
   * {@code size}. An element index may range from zero, inclusive, to {@code size}, exclusive.
   *
   * @param index a user-supplied index identifying an element of an array, collection or string
   * @param size  the size of that array, collection or string
   * @throws IndexOutOfBoundsException if {@code index} is negative or is not less than {@code size}
   * @throws IllegalArgumentException  if {@code size} is negative
   * @apiNote Guava's {@code com.google.common.base.Preconditions#checkElementIndex(int, int)} analogue.
   */
  public static void checkIndex(int index, int size) {
    checkIndex(index, size, "index");
  }

  /**
   * Ensures that {@code index} specifies a valid <i>element</i> in an array, collection or string of size
   * {@code size}. An element index may range from zero, inclusive, to {@code size}, exclusive.
   *
   * @param index            a user-supplied index identifying an element of an array, collection or string
   * @param size             the size of that array, collection or string
   * @param indexDescription the text to use to describe this index in an error message
   * @throws IndexOutOfBoundsException if {@code index} is negative or is not less than {@code size}
   * @throws IllegalArgumentException  if {@code size} is negative
   * @apiNote Guava's {@code com.google.common.base.Preconditions#checkElementIndex(int, int, java.lang.String)} analogue.
   */
  public static void checkIndex(int index, int size, @NotNull String indexDescription) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(badElementIndex(index, size, indexDescription));
    }
  }

  /**
   * Analogue of the {@link #checkIndex(int, int)} which logs instead of throwing an error.
   */
  public static void checkIndexAndLog(@NotNull Class<?> loggingClass, int index, int size) {
    checkIndexAndLog(loggingClass, index, size, "index");
  }

  /**
   * Analogue of the {@link #checkIndex(int, int, String)} which logs instead of throwing an error.
   */
  public static void checkIndexAndLog(@NotNull Class<?> loggingClass, int index, int size, @NotNull String indexDescription) {
    if (index < 0 || index >= size) {
      Logger.getInstance(loggingClass).error(new IndexOutOfBoundsException(badElementIndex(index, size, indexDescription)));
    }
  }

  /**
   * Analogue of the {@link #checkIndex(int, int, String)} which logs with {@code attachments} provided instead of throwing an error.
   */
  public static void checkIndexAndLog(
    @NotNull Class<?> loggingClass,
    int index,
    int size,
    @NotNull String indexDescription,
    Attachment @NotNull ... attachments
  ) {
    if (index < 0 || index >= size) {
      String message = badElementIndex(index, size, indexDescription);
      Logger.getInstance(loggingClass).error(message, new IndexOutOfBoundsException(message), attachments);
    }
  }

  private static @NotNull String badElementIndex(int index, int size, @NotNull String indexDescription) {
    if (index < 0) {
      return indexDescription + " (" + index + ") must not be negative";
    }
    else if (size < 0) {
      throw new IllegalArgumentException("negative size: " + size);
    }
    else { // index >= size
      return indexDescription + " (" + index + ") must be less than size (" + size + ")";
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region Postconditions, output assertions

  /**
   * @throws IllegalPostconditionException if the {@code statement} is {@code false}
   */
  @Contract("false -> fail")
  public static void ensure(boolean statement) {
    if (!statement) {
      throw new IllegalPostconditionException(POSTCONDITION_IS_NOT_SATISFIED);
    }
  }

  /**
   * @throws IllegalPostconditionException with the result of calling {@code lazyMessage}
   *                                       if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void ensure(boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      throw new IllegalPostconditionException(lazyMessage.get().toString());
    }
  }

  /**
   * @throws IllegalPostconditionException with the given {@code message} if the {@code statement} is {@code false}.
   */
  @Contract("false, _ -> fail")
  public static void ensure(boolean statement, @NotNull String message) {
    if (!statement) {
      throw new IllegalPostconditionException(message);
    }
  }

  /**
   * @throws IllegalPostconditionException with the given {@code message}
   *                                       if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void ensure(@NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalPostconditionException(message);
    }
  }

  /**
   * @throws IllegalPostconditionException with the result of calling {@code lazyMessage}
   *                                       if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void ensure(@NotNull Supplier<Object> lazyMessage, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      throw new IllegalPostconditionException(lazyMessage.get().toString());
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} if the {@code statement} is {@code false}
   */
  public static void ensureAndLog(@NotNull Class<?> loggingClass, boolean statement) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalPostconditionException(POSTCONDITION_IS_NOT_SATISFIED));
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} with the result of calling {@code lazyMessage}
   * if the {@code statement} is {@code false}.
   */
  public static void ensureAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull Supplier<Object> lazyMessage) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalPostconditionException(lazyMessage.get().toString()));
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} with the given {@code message} if the {@code statement} is {@code false}.
   */
  public static void ensureAndLog(@NotNull Class<?> loggingClass, boolean statement, @NotNull String message) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(new IllegalPostconditionException(message));
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} with the given {@code message} and {@code attachments}
   * if the {@code statement} is {@code false}.
   */
  public static void ensureAndLog(
    @NotNull Class<?> loggingClass,
    boolean statement,
    @NotNull String message,
    Attachment @NotNull ... attachments
  ) {
    if (!statement) {
      Logger.getInstance(loggingClass).error(message, new IllegalPostconditionException(message), attachments);
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} with the given {@code message}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void ensureAndLog(@NotNull Class<?> loggingClass, @NotNull String message, @NotNull BooleanSupplier statementSupplier) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalPostconditionException(message));
    }
  }

  /**
   * Logs {@link IllegalPostconditionException} with the result of calling {@code lazyMessage}
   * if the {@code statementSupplier.getAsBoolean()} is {@code false}.
   */
  public static void ensureAndLog(
    @NotNull Class<?> loggingClass,
    @NotNull Supplier<Object> lazyMessage,
    @NotNull BooleanSupplier statementSupplier
  ) {
    if (!statementSupplier.getAsBoolean()) {
      Logger.getInstance(loggingClass).error(new IllegalPostconditionException(lazyMessage.get().toString()));
    }
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /* ------------------------------------------------------------------------------------------- */
  //region Error throwing / logging

  /**
   * @throws IllegalStateException with the given {@code message}.
   */
  @Contract("_ -> fail")
  public static void fail(@NotNull Object message) {
    throw new IllegalStateException(message.toString());
  }

  /**
   * @throws IllegalStateException saying that this function call must have been unreachable.
   */
  @Contract("-> fail")
  public static void unreachable() {
    throw new IllegalStateException("Must be unreachable");
  }

  /**
   * Logs {@link IllegalStateException} with the given {@code message} and {@code attachments} as an <i>error</i>.
   */
  public static void logError(@NotNull Class<?> loggingClass, @NotNull Object message, Attachment @NotNull ... attachments) {
    String s = message.toString();
    Logger.getInstance(loggingClass).error(s, new IllegalStateException(s), attachments);
  }

  //endregion
  /* ------------------------------------------------------------------------------------------- */


  /**
   * Indicates that some postcondition or invariant over results and other output values was not satisfied.
   * It is semantically close to {@link IllegalArgumentException} but for method results.
   */
  public static final class IllegalPostconditionException extends RuntimeException {
    public IllegalPostconditionException() {
    }

    public IllegalPostconditionException(String message) {
      super(message);
    }

    public IllegalPostconditionException(String message, Throwable cause) {
      super(message, cause);
    }

    public IllegalPostconditionException(Throwable cause) {
      super(cause);
    }

    public IllegalPostconditionException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
      super(message, cause, enableSuppression, writableStackTrace);
    }
  }
}
