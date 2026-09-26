// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.IOException;

public class ExceptionUtilTest {

  @Test
  public void findCauseAndSuppressed() {
    Throwable exc = new RuntimeException("exc", new RuntimeException("exc-cause", new IllegalStateException("exc-cause-cause")));
    exc.addSuppressed(new RuntimeException("exc-suppressed"));
    exc.getCause().addSuppressed(new IllegalStateException("exc-cause-suppressed"));

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, Throwable.class), Throwable::getMessage))
      .containsExactly("exc", "exc-cause", "exc-cause-cause",  "exc-suppressed", "exc-cause-suppressed");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, IllegalStateException.class), Throwable::getMessage))
      .containsExactly("exc-cause-cause", "exc-cause-suppressed");
  }

  @Test
  public void findCauseAndSuppressedCircularReferences() {
    RuntimeException cause = new RuntimeException("exc-cause", new IllegalStateException("exc-cause-cause"));
    Throwable exc = new RuntimeException("exc", cause);
    cause.addSuppressed(exc);

    exc.addSuppressed(new RuntimeException("exc-suppressed"));
    IllegalStateException suppressed = new IllegalStateException("exc-cause-suppressed");
    exc.getCause().addSuppressed(suppressed);
    suppressed.addSuppressed(exc);

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, Throwable.class), Throwable::getMessage))
      .containsExactly("exc", "exc-cause", "exc-cause-cause",  "exc-suppressed", "exc-cause-suppressed");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(exc, IllegalStateException.class), Throwable::getMessage))
      .containsExactly("exc-cause-cause", "exc-cause-suppressed");
  }

  // findCause, causedBy, getRootCause, causeAndSuppressed/findCauseAndSuppressed, getMessage and unwrapException
  // all walk getCause() with no record of where they have been, so a chain that loops back on itself used to
  // spin forever; the tests below build genuine cause cycles to guard against that regressing.

  /**
   * A throwable that is its own immediate cause. `Throwable.initCause` refuses `cause == this`
   * ("Self-causation not permitted"), so this shape can only be produced by overriding {@link #getCause()} to
   * return {@code this} -- a legitimate "no delegate cause" idiom, distinct from a genuine multi-node cycle, that
   * the cause-chain walk must not mistake for a genuine cycle.
   */
  private static final class SelfCausingException extends RuntimeException {
    SelfCausingException(String message) {
      super(message);
    }

    @Override
    public synchronized Throwable getCause() {
      return this;
    }
  }

  /**
   * first -&gt; second -&gt; first -&gt; second -&gt; ...
   * <p>
   * {@code Throwable(String)} leaves {@code cause} uninitialised (equal to {@code this}), so
   * {@code second.initCause(first)} can close the loop.
   */
  private static RuntimeException twoNodeCycle(String firstMessage, String secondMessage) {
    RuntimeException second = new RuntimeException(secondMessage);
    RuntimeException first = new RuntimeException(firstMessage, second);
    second.initCause(first);
    return first;
  }

  /** a -&gt; b -&gt; c -&gt; a -&gt; b -&gt; c -&gt; ... */
  private static RuntimeException threeNodeCycle(String aMessage, String bMessage, String cMessage) {
    RuntimeException c = new RuntimeException(cMessage);
    RuntimeException b = new RuntimeException(bMessage, c);
    RuntimeException a = new RuntimeException(aMessage, b);
    c.initCause(a);
    return a;
  }

  /** "root" -&gt; "mid" -&gt; {@code cycleEntry}, i.e. the cycle's entry point is two acyclic links downstream. */
  private static RuntimeException withAcyclicPrefix(Throwable cycleEntry) {
    RuntimeException mid = new RuntimeException("mid", cycleEntry);
    return new RuntimeException("root", mid);
  }

  // -- findCause / causedBy --------------------------------------------------------------------

  @Test
  public void findCauseNullThrowableReturnsNull() {
    Assertions.assertThat(ExceptionUtilRt.findCause(null, IOException.class)).isNull();
  }

  @Test
  public void findCauseTwoNodeCycleReturnsNull() {
    RuntimeException first = twoNodeCycle("first", "second");
    Assertions.assertThat(ExceptionUtil.findCause(first, IOException.class)).isNull();
  }

  /** Also exercises the tortoise/hare parity: the cycle isn't detected on the first lap. */
  @Test
  public void findCauseLongerCycleReturnsNull() {
    RuntimeException a = threeNodeCycle("a", "b", "c");
    Assertions.assertThat(ExceptionUtil.findCause(a, IOException.class)).isNull();
  }

  @Test
  public void findCauseCycleAfterAcyclicPrefixReturnsNull() {
    RuntimeException root = withAcyclicPrefix(twoNodeCycle("first", "second"));
    Assertions.assertThat(ExceptionUtil.findCause(root, IOException.class)).isNull();
  }

  @Test
  public void findCauseSelfCauseReturnsNull() {
    Assertions.assertThat(ExceptionUtil.findCause(new SelfCausingException("boom"), IOException.class)).isNull();
  }

  /**
   * {@code outer -> inner}, where {@code inner} is its own cause and matches {@code classToUnwrap}'s counterpart
   * here, the target class. The walk must reach {@code inner} without the self-cause node being mistaken for a
   * cycle (which would return null instead of finding the match).
   */
  @Test
  public void findCauseSelfCauseAtDepthFindsInnerNode() {
    SelfCausingException inner = new SelfCausingException("inner");
    RuntimeException outer = new RuntimeException("outer", inner);
    Assertions.assertThat(ExceptionUtil.findCause(outer, SelfCausingException.class)).isSameAs(inner);
  }

  @Test
  public void findCauseAcyclicUnchanged() {
    IllegalStateException target = new IllegalStateException("target");
    RuntimeException chain = new RuntimeException("outer", new RuntimeException("middle", target));
    Assertions.assertThat(ExceptionUtil.findCause(chain, IllegalStateException.class)).isSameAs(target);
    Assertions.assertThat(ExceptionUtil.findCause(chain, IOException.class)).isNull();
  }

  @Test
  public void causedByCycleReturnsFalseWithoutHanging() {
    RuntimeException first = twoNodeCycle("first", "second");
    Assertions.assertThat(ExceptionUtil.causedBy(first, IOException.class)).isFalse();
  }

  @Test
  public void causedByAcyclicUnchanged() {
    RuntimeException chain = new RuntimeException("outer", new RuntimeException("middle", new IllegalStateException("target")));
    Assertions.assertThat(ExceptionUtil.causedBy(chain, IllegalStateException.class)).isTrue();
    Assertions.assertThat(ExceptionUtil.causedBy(chain, IOException.class)).isFalse();
  }

  // -- getRootCause -----------------------------------------------------------------------------

  /**
   * The Floyd meeting node is an arithmetic accident of prefix/cycle length, so this only pins that the walk
   * terminates and lands on a member of the cycle -- not which member.
   */
  @Test
  public void getRootCauseTwoNodeCycleReturnsCycleMember() {
    RuntimeException first = twoNodeCycle("first", "second");
    Throwable second = first.getCause();
    Assertions.assertThat(ExceptionUtil.getRootCause(first)).isIn(first, second);
  }

  /** Also exercises the tortoise/hare parity: the cycle isn't detected on the first lap. */
  @Test
  public void getRootCauseLongerCycleReturnsCycleMember() {
    RuntimeException a = threeNodeCycle("a", "b", "c");
    Throwable b = a.getCause();
    Throwable c = b.getCause();
    Assertions.assertThat(ExceptionUtil.getRootCause(a)).isIn(a, b, c);
  }

  @Test
  public void getRootCauseCycleAfterAcyclicPrefixReturnsCycleMember() {
    RuntimeException cycleEntry = threeNodeCycle("a", "b", "c");
    Throwable b = cycleEntry.getCause();
    Throwable c = b.getCause();
    RuntimeException root = withAcyclicPrefix(cycleEntry);
    Assertions.assertThat(ExceptionUtil.getRootCause(root)).isIn(cycleEntry, b, c);
  }

  @Test
  public void getRootCauseSelfCauseReturnsSelf() {
    SelfCausingException e = new SelfCausingException("boom");
    Assertions.assertThat(ExceptionUtil.getRootCause(e)).isSameAs(e);
  }

  @Test
  public void getRootCauseAcyclicUnchanged() {
    RuntimeException root = new RuntimeException("root");
    RuntimeException chain = new RuntimeException("outer", new RuntimeException("middle", root));
    Assertions.assertThat(ExceptionUtil.getRootCause(chain)).isSameAs(root);
    Assertions.assertThat(ExceptionUtil.getRootCause(root)).isSameAs(root);
  }

  /**
   * {@code outer -> inner}, where {@code inner} is its own cause. {@code inner} is the deepest node reachable
   * without stepping onto a self-cause, so it -- not {@code outer} -- is the root cause; the walk must not treat
   * the self-cause as a genuine multi-node cycle and stop early.
   */
  @Test
  public void getRootCauseSelfCauseAtDepthReturnsInnerNode() {
    SelfCausingException inner = new SelfCausingException("inner");
    RuntimeException outer = new RuntimeException("outer", inner);
    Assertions.assertThat(ExceptionUtil.getRootCause(outer)).isSameAs(inner);
  }

  // -- getMessage ---------------------------------------------------------------------------------
  //
  // Every node in the cycles below has a null message, so the expected result (null) does not depend on
  // exactly which node the walk happens to stop at.

  @Test
  public void getMessageTwoNodeCycleReturnsNullWithoutHanging() {
    RuntimeException second = new RuntimeException((String)null);
    RuntimeException first = new RuntimeException(null, second);
    second.initCause(first);
    Assertions.assertThat(ExceptionUtil.getMessage(first)).isNull();
  }

  @Test
  public void getMessageSelfCauseReturnsNullWithoutHanging() {
    Assertions.assertThat(ExceptionUtil.getMessage(new SelfCausingException(null))).isNull();
  }

  /**
   * {@code outer -> inner}, where {@code inner} is its own cause and has an informative message. The walk must
   * descend from {@code outer} (null message) to {@code inner}'s message without ever asking the cursor to step
   * past {@code inner} onto itself.
   */
  @Test
  public void getMessageSelfCauseAtDepthReturnsInnerMessage() {
    SelfCausingException inner = new SelfCausingException("inner detail");
    RuntimeException outer = new RuntimeException(null, inner);
    Assertions.assertThat(ExceptionUtil.getMessage(outer)).isEqualTo("inner detail");
  }

  /**
   * Same as {@link #getMessageSelfCauseAtDepthReturnsInnerMessage}, but {@code inner}'s own message matches the
   * "Exception: " strip pattern, so this exercises the case where the chain running out of causes at {@code inner}
   * -- not message content -- is what stops the walk there.
   */
  @Test
  public void getMessageSelfCauseWithPatternMessageAtDepthReturnsStrippedMessage() {
    SelfCausingException inner = new SelfCausingException("Exception: useful");
    RuntimeException outer = new RuntimeException(null, inner);
    Assertions.assertThat(ExceptionUtil.getMessage(outer)).isEqualTo("useful");
  }

  @Test
  public void getMessageAcyclicUnchanged() {
    Throwable inner = new RuntimeException("real cause message");
    Throwable middle = new RuntimeException("java.lang.RuntimeException: wrapper", inner);
    Assertions.assertThat(ExceptionUtil.getMessage(middle)).isEqualTo("real cause message");
    Assertions.assertThat(ExceptionUtil.getMessage(inner)).isEqualTo("real cause message");
  }

  /**
   * Both nodes have a distinct, pattern-matching message, so the walk never stops early on message content and
   * runs until the cycle is detected. Pins the deliberate behaviour change documented on {@link ExceptionUtil#getMessage}:
   * the result is now {@code first}'s message -- the last node actually visited -- rather than {@code second}'s,
   * which the pre-iterator implementation returned (one hop behind where the walk stopped).
   */
  @Test
  public void getMessageCycleReturnsLastVisitedNodeMessage() {
    RuntimeException second = new RuntimeException("Exception: from second");
    RuntimeException first = new RuntimeException("Exception: from first", second);
    second.initCause(first);
    Assertions.assertThat(ExceptionUtil.getMessage(first)).isEqualTo("from first");
  }

  // -- unwrapException ------------------------------------------------------------------------------

  @Test
  public void unwrapExceptionTwoNodeCycleReturnsOriginal() {
    RuntimeException first = twoNodeCycle("first", "second");
    Assertions.assertThat(ExceptionUtil.unwrapException(first, RuntimeException.class)).isSameAs(first);
  }

  @Test
  public void unwrapExceptionSelfCauseUnchanged() {
    SelfCausingException e = new SelfCausingException("boom");
    Assertions.assertThat(ExceptionUtil.unwrapException(e, RuntimeException.class)).isSameAs(e);
  }

  /**
   * {@code outer -> inner}, where {@code inner} is its own cause. The walk must stop peeling at {@code inner}
   * rather than let the cursor advance once more and detect a "cycle" that returns the still-wrapped {@code outer}
   * instead.
   */
  @Test
  public void unwrapExceptionSelfCauseAtDepthReturnsPeeledNode() {
    SelfCausingException inner = new SelfCausingException("inner");
    RuntimeException outer = new RuntimeException("outer", inner);
    Assertions.assertThat(ExceptionUtil.unwrapException(outer, RuntimeException.class)).isSameAs(inner);
  }

  @Test
  public void unwrapExceptionAcyclicUnchanged() {
    IOException inner = new IOException("boom");
    RuntimeException w1 = new RuntimeException("w1", inner);
    RuntimeException w2 = new RuntimeException("w2", w1);
    Assertions.assertThat(ExceptionUtil.unwrapException(w2, RuntimeException.class)).isSameAs(inner);
  }

  @Test
  public void unwrapExceptionTypeMismatchReturnsOriginalUnchanged() {
    IOException e = new IOException("boom");
    Assertions.assertThat(ExceptionUtil.unwrapException(e, RuntimeException.class)).isSameAs(e);
  }

  @Test
  public void unwrapExceptionLeafStillMatchingReturnsLeaf() {
    RuntimeException leaf = new RuntimeException("leaf");
    Assertions.assertThat(ExceptionUtil.unwrapException(leaf, RuntimeException.class)).isSameAs(leaf);
  }

  // -- causeAndSuppressed / findCauseAndSuppressed --------------------------------------------------
  //
  // For genuinely cyclic input the exact insertion order among cycle members is an implementation detail, so
  // these assert the resulting *set* rather than an exact order; findCauseAndSuppressedAcyclicOrderPreservedForLongerChain
  // below is what pins down the order guarantee that must not regress.

  @Test
  public void findCauseAndSuppressedCauseCycleWithSuppressedTerminates() {
    RuntimeException second = new RuntimeException("second");
    RuntimeException first = new RuntimeException("first", second);
    second.initCause(first);
    first.addSuppressed(new IllegalStateException("suppressed-on-first"));
    second.addSuppressed(new IllegalStateException("suppressed-on-second"));

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(first, Throwable.class), Throwable::getMessage))
      .containsExactlyInAnyOrder("first", "second", "suppressed-on-first", "suppressed-on-second");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(first, IllegalStateException.class), Throwable::getMessage))
      .containsExactlyInAnyOrder("suppressed-on-first", "suppressed-on-second");
  }

  @Test
  public void findCauseAndSuppressedSelfCauseTerminates() {
    SelfCausingException e = new SelfCausingException("boom");
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(e, Throwable.class), Throwable::getMessage))
      .containsExactly("boom");
  }

  @Test
  public void findCauseAndSuppressedSelfCauseAtDepthTerminates() {
    SelfCausingException inner = new SelfCausingException("inner");
    RuntimeException outer = new RuntimeException("outer", inner);
    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(outer, Throwable.class), Throwable::getMessage))
      .containsExactly("outer", "inner");
  }

  @Test
  public void findCauseAndSuppressedAcyclicOrderPreservedForLongerChain() {
    IllegalStateException innermost = new IllegalStateException("innermost");
    RuntimeException middle = new RuntimeException("middle", innermost);
    RuntimeException outer = new RuntimeException("outer", middle);
    outer.addSuppressed(new RuntimeException("outer-suppressed"));

    Assertions
      .assertThat(ContainerUtil.map(ExceptionUtil.findCauseAndSuppressed(outer, Throwable.class), Throwable::getMessage))
      .containsExactly("outer", "middle", "innermost", "outer-suppressed");
  }
}
