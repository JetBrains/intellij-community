/*--

 Copyright (C) 2011 - 2012 Jason Hunter & Brett McLaughlin.
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions
 are met:

 1. Redistributions of source code must retain the above copyright
    notice, this list of conditions, and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions, and the disclaimer that follows
    these conditions in the documentation and/or other materials
    provided with the distribution.

 3. The name "JDOM" must not be used to endorse or promote products
    derived from this software without prior written permission.  For
    written permission, please contact <request_AT_jdom_DOT_org>.

 4. Products derived from this software may not be called "JDOM", nor
    may "JDOM" appear in their name, without prior written permission
    from the JDOM Project Management <request_AT_jdom_DOT_org>.

 In addition, we request (but do not require) that you include in the
 end-user documentation provided with the redistribution and/or in the
 software itself an acknowledgement equivalent to the following:
     "This product includes software developed by the
      JDOM Project (http://www.jdom.org/)."
 Alternatively, the acknowledgment may be graphical using the logos
 available at http://www.jdom.org/images/logos.

 THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED.  IN NO EVENT SHALL THE JDOM AUTHORS OR THE PROJECT
 CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 SUCH DAMAGE.

 This software consists of voluntary contributions made by many
 individuals on behalf of the JDOM Project and was originally
 created by Jason Hunter <jhunter_AT_jdom_DOT_org> and
 Brett McLaughlin <brett_AT_jdom_DOT_org>.  For more information
 on the JDOM Project, please see <http://www.jdom.org/>.

 */

package org.jdom.util;

import org.jdom.Attribute;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jetbrains.annotations.NotNull;

import java.util.*;


/**
 * A high-performance stack for processing those Namespaces that are introduced
 * or are in-scope at a point in an Element hierarchy.
 * <p>
 * This stack implements the 'Namespace Rules' which XML uses, where a Namespace
 * 'redefines' an existing Namespace if they share the same prefix. This class
 * is intended to provide a high-performance mechanism for calculating the
 * Namespace scope for an Element, and identifying what Namespaces an Element
 * introduces in to the scope. This is not a validation tool.
 * <p>
 * This class implements Iterable which means it can be used in the context
 * of a for-each type loop:
 * <br>
 * <code><pre>
 *   NamespaceStack namespacestack = new NamespaceStack();
 *   for (Namespace ns : namespacestack) {
 *      ...
 *   }
 * </pre></code>
 * The Iteration in the above example will return those Namespaces which are
 * in-scope for the current level of the stack. The Namespace order will follow
 * the JDOM 'standard'. The first namespace will be the Element's Namespace. The
 * subsequent Namespaces will be the other in-scope namespaces in alphabetical
 * order by the Namespace prefix.
 * <p>
 * NamespaceStack does not validate the push()/pop() cycles. It does not ensure
 * that the pop() is for the same element that was previously pushed. Further,
 * it does not check to make sure that the pushed() Element is the natural child
 * of the previously pushed() Element.
 *
 * @author Rolf Lear
 */
public final class NamespaceStack implements Iterable<Namespace> {

  /**
   * Simple read-only iterator that walks an array of Namespace.
   *
   * @author rolf
   */
  private static final class ForwardWalker implements Iterator<Namespace> {
    private final Namespace[] namespaces;
    private int cursor = 0;

    private ForwardWalker(Namespace[] namespaces) {
      this.namespaces = namespaces;
    }

    @Override
    public boolean hasNext() {
      return cursor < namespaces.length;
    }

    @Override
    public Namespace next() {
      if (cursor >= namespaces.length) {
        throw new NoSuchElementException("Cannot over-iterate...");
      }
      return namespaces[cursor++];
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
        "Cannot remove Namespaces from iterator");
    }
  }

  /**
   * Simple read-only iterator that walks an array of Namespace in reverse.
   *
   * @author rolf
   */
  private static final class BackwardWalker implements Iterator<Namespace> {
    private final Namespace[] namespaces;
    private int cursor;

    BackwardWalker(Namespace[] namespaces) {
      this.namespaces = namespaces;
      cursor = namespaces.length - 1;
    }

    @Override
    public boolean hasNext() {
      return cursor >= 0;
    }

    @Override
    public Namespace next() {
      if (cursor < 0) {
        throw new NoSuchElementException("Cannot over-iterate...");
      }
      return namespaces[cursor--];
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
        "Cannot remove Namespaces from iterator");
    }
  }

  /**
   * Simple Iterable instance that produces either Forward or Backward
   * read-only iterators of the Namespaces
   *
   * @author rolf
   */
  private static final class NamespaceIterable implements Iterable<Namespace> {
    private final boolean forward;
    private final Namespace[] namespaces;

    private NamespaceIterable(Namespace[] data, boolean forward) {
      this.forward = forward;
      this.namespaces = data;
    }

    @Override
    public @NotNull Iterator<Namespace> iterator() {
      return forward ? new ForwardWalker(namespaces)
                     : new BackwardWalker(namespaces);
    }
  }

  /**
   * Convenience class that makes very fast work for an empty Namespace array.
   * It doubles up as both Iterator and Iterable.
   *
   * @author rolf
   */
  private static final class EmptyIterable
    implements Iterable<Namespace>, Iterator<Namespace> {
    @Override
    public Iterator<Namespace> iterator() {
      return this;
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Namespace next() {
      throw new NoSuchElementException(
        "Can not call next() on an empty Iterator.");
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException(
        "Cannot remove Namespaces from iterator");
    }
  }

  /**
   * A simple empty Namespace Array to avoid redundant empty instances
   */
  private static final Namespace[] EMPTY = new Namespace[0];
  /**
   * A simple Iterable instance that is always empty. Saves some memory
   */
  private static final Iterable<Namespace> EMPTYITER = new EmptyIterable();

  /**
   * A comparator that sorts Namespaces by their prefix.
   */
  private static final Comparator<Namespace> NSCOMP = Comparator.comparing(Namespace::getPrefix);
  private static final Namespace[] DEFAULTSEED = new Namespace[]{
    Namespace.NO_NAMESPACE, Namespace.XML_NAMESPACE};

  /**
   * Lots of reasons for having our own binarySearch.
   * <ul>
   * <li> We can make it specific for Namespaces (using == search).
   * <li> There is a bug in IBM's AIX JVM in all Java's prior to (including):
   *      IBM J9 VM (build 2.4, J2RE 1.6.0 IBM J9 2.4 AIX ppc-32
   *              jvmap3260-20081105_25433 (JIT enabled, AOT enabled))
   *      where it returns '-1' for all instances where 'from == to' instead
   *      of returning '-from -1'. See
   *      <a href="http://www.ibm.com/developerworks/forums/thread.jspa?threadID=351575&tstart=0">
   *      this description</a> for how it is broken, and pre-checking to make
   *      sure that <code>left &lt; right</code> for each test is a pain.
   * <li> Ahh, actually, we will never encounter the bug, because we always
   *      have a larger-than-1 scope array.... see comment inside code...
   * <li> It's not that complicated, really.
   * </ul>
   *
   * @param data  The Namespaces to search.
   * @param left  The left side of the range to search <b>INCLUSIVE</b>
   * @param right The right side of the range to search <b>EXCLUSIVE</b>
   * @param key   The Namespace to search for.
   * @return the 'insertion point' - This return value follows the same convention
   * as the standard Java BinarySearch methods (see the JavaDoc for Arrays.binarySearch().
   * In summary, if the value exists then the return value is the index of the existing value.
   * If the value was not found, then the return value will be negative, and the
   * place where the missing value should be inserted, can be determined by
   * adding 1, and converting back to positive (or converting to positive, and subtracting 1).
   * </i>
   */
  private static int binarySearch(final Namespace[] data,
                                  int left, int right, final Namespace key) {
    // assume all input is valid. No need to waste time checking.

    //		Because we are always searching inside of the scope array, and
    //		because there's always at least two scope members, we will always have
    //		a minimum value of 2 for 'right', and a maximum value of 1 for 'left'
    //		thus the following check is never needed.
    //
    //		if (left >= right) {
    //			// we are searching in nothing, return the correct value
    //			// ... this is where IBM's JDK is broken - it just returns -1
    //			return -left - 1;
    //		}
    // make the right-side 'inclusive' instead of 'exclusive'
    right--;

    while (left <= right) {
      // get the mid-point. See the notes on the binary-search bug...
      // ... not that we'll ever have that many Namspaces.... ;-)
      // http://googleresearch.blogspot.com/2006/06/extra-extra-read-all-about-it-nearly.html
      final int mid = (left + right) >>> 1;
      if (data[mid] == key) {
        // exact namespace match.
        return mid;
      }
      final int cmp = NSCOMP.compare(data[mid], key);

      if (cmp < 0) {
        left = mid + 1;
      }
      else if (cmp > 0) {
        right = mid - 1;
      }
      else {
        // Namespace prefix match.
        return mid;
      }
    }
    return -left - 1;
  }

  /**
   * The namespaces added to the scope at each depth
   */
  private Namespace[][] added = new Namespace[10][];
  /**
   * The entire scope at each depth
   */
  private Namespace[][] scope = new Namespace[10][];
  /**
   * The current depth
   */
  private int depth = -1;

  /**
   * Create a NamespaceWalker ready to use as a stack.
   * <br>
   *
   * @see #push(Element) for comprehensive notes.
   */
  public NamespaceStack() {
    this(DEFAULTSEED);
  }

  /**
   * Create a NamespaceWalker ready to use as a stack.
   * <br>
   *
   * @param seed The namespaces to set as the top level of the stack.
   * @see #push(Element) for comprehensive notes.
   */
  public NamespaceStack(Namespace[] seed) {
    depth++;
    added[depth] = seed;

    scope[depth] = added[depth];
  }

  /**
   * Inspect the <i>scope</i> array to see whether the <i>namespace</i>
   * Namespace is 'new' or not. If it is 'new' then it is added to the
   * <i>store</i> List.
   *
   * @param store     Where to add the <i>namespace</i> if it is 'new'
   * @param namespace The Namespace to check
   * @param scope     The array of Namespaces that are currently in-scope.
   * @return The revised version of 'in-scope' if the scope has changed. If
   * there is no modification then the same input scope will be returned.
   */
  private static Namespace[] checkNamespace(List<? super Namespace> store,
                                            Namespace namespace, Namespace[] scope) {
    // Scope is always sorted as the primary namespace first, then the
    // rest are in prefix order.
    // We can guarantee that the prefixes are all unique too.
    // There is always going to be at least two namespaces in scope with
    // the prefixes : "" and "xml"
    // As a result we can use the 0th index with impunity.
    if (namespace == scope[0]) {
      // we are already in scope.
      return scope;
    }
    if (namespace.getPrefix().equals(scope[0].getPrefix())) {
      // the prefix is the previous scope's primary prefix. This means
      // that we know for sure that the input namespace is new-to-scope.
      store.add(namespace);
      final Namespace[] nscope = scope.clone();
      nscope[0] = namespace;
      return nscope;
    }
    // will return +ve number if the prefix matches too.
    int ip = binarySearch(scope, 1, scope.length, namespace);
    if (ip >= 0 && namespace == scope[ip]) {
      // the namespace is already in scope.
      return scope;
    }
    store.add(namespace);
    if (ip >= 0) {
      // a different namespace with the same prefix as us is in-scope.
      // replace it....
      final Namespace[] nscope = scope.clone();
      nscope[ip] = namespace;
      return nscope;
    }
    // We are a new prefix in-scope.
    final Namespace[] nscope = Arrays.copyOf(scope, scope.length + 1);
    ip = -ip - 1;
    System.arraycopy(nscope, ip, nscope, ip + 1, nscope.length - ip - 1);
    nscope[ip] = namespace;
    return nscope;
  }

  /**
   * Create a new in-scope level for the Stack based on an Element.
   * <br>
   * The Namespaces associated with the input Element are used to modify the
   * 'in-scope' Namespaces in this NamespaceStack.
   * <br>
   * The following 'rules' will be applied:
   * <ul>
   * <li>Namespaces used in the input Element that were not part of the previous
   *     scope will be added to the new scope level in the stack.
   * <li>If a new Namespace is added to the scope, but the previous scope
   *     already had a namespace with the same prefix, then that previous
   *     namespace is removed from the new scope (the new Namespace replaces
   *     the previous namespace with the same prefix).
   * <li>The order of the in-scope Namespaces will always be: first the
   *     Namespace of the input Element followed by all other in-scope
   *     Namespaces sorted alphabetically by prefix.
   * <li>The new in-scope Namespace values will be available in this class's
   *     iterator() method (which is available as part of this class's
   *     <i>Iterable</i> implementation.
   * <li>The namespaces added to the scope by the input Element will be
   *     available in the {@link #addedForward()} Iterable. The order of
   *     the added Namespaces follows the same rules as above: first the
   *     Element Namespace (only if that Namespace is actually added) followed
   *     by the other added namespaces in alphabetical-by-prefix order.
   * </ul>
   *
   * @param element The element at the new level of the stack.
   */
  public void push(Element element) {

    // how many times do you add more than 8 namespaces in one go...
    // we can add more if we need to...
    final List<Namespace> toadd = new ArrayList<>(8);
    final Namespace mns = element.getNamespace();
    // check to see whether the Namespace is new-to-scope.
    Namespace[] newscope = checkNamespace(toadd, mns, scope[depth]);
    if (element.hasAdditionalNamespaces()) {
      for (final Namespace ns : element.getAdditionalNamespaces()) {
        if (ns == mns) {
          continue;
        }
        // check to see whether the Namespace is new-to-scope.
        newscope = checkNamespace(toadd, ns, newscope);
      }
    }
    if (element.hasAttributes()) {
      for (final Attribute a : element.getAttributes()) {
        final Namespace ns = a.getNamespace();
        if (ns == Namespace.NO_NAMESPACE) {
          // Attributes are allowed to be in the NO_NAMESPACE without
          // changing the in-scope set of the Element.... special-case
          continue;
        }
        if (ns == mns) {
          continue;
        }
        // check to see whether the Namespace is new-to-scope.
        newscope = checkNamespace(toadd, ns, newscope);
      }
    }

    pushStack(mns, newscope, toadd);
  }

  /**
   * Create a new in-scope level for the Stack based on an Attribute.
   *
   * @param att The attribute to contribute to the namespace scope.
   */
  public void push(Attribute att) {
    final List<Namespace> toadd = new ArrayList<>(1);
    final Namespace mns = att.getNamespace();
    // check to see whether the Namespace is new-to-scope.
    Namespace[] newscope = checkNamespace(toadd, mns, scope[depth]);

    pushStack(mns, newscope, toadd);
  }

  private void pushStack(final Namespace mns, Namespace[] newscope,
                         final List<Namespace> toadd) {
    // OK, we've checked the namespaces in the Element, and 'toadd' contains
    // all namespaces that are not already in scope.
    depth++;

    if (depth >= scope.length) {
      // we need more space on the stack.
      scope = Arrays.copyOf(scope, scope.length * 2);
      added = Arrays.copyOf(added, scope.length);
    }

    // Sort out the added namespaces.
    if (toadd.isEmpty()) {
      // nothing changed in the scope.
      added[depth] = EMPTY;
    }
    else {
      added[depth] = toadd.toArray(new Namespace[0]);
      if (added[depth][0] == mns) {
        Arrays.sort(added[depth], 1, added[depth].length, NSCOMP);
      }
      else {
        Arrays.sort(added[depth], NSCOMP);
      }
    }

    if (mns != newscope[0]) {
      if (toadd.isEmpty()) {
        // we need to make newscope a copy of the previous level's
        // scope, because it is not yet a copy.
        newscope = newscope.clone();
      }
      // we need to take the Namespace at position 0, and insert it
      // in it's place later in the array.
      // we need to take the mns from later in the array, and move it
      // to the front.
      final Namespace tmp = newscope[0];
      int ip = -binarySearch(newscope, 1, newscope.length, tmp) - 1;
      // we can be sure that (- ip - 1 ) is >= 1
      // we also know that we want to move the data before the ip
      // backwards one spot, so the math is slightly different....
      ip--;
      System.arraycopy(newscope, 1, newscope, 0, ip);
      newscope[ip] = tmp;

      ip = binarySearch(newscope, 0, newscope.length, mns);
      // we can be sure that ip is >= 0
      System.arraycopy(newscope, 0, newscope, 1, ip);
      newscope[0] = mns;
    }

    scope[depth] = newscope;
  }

  /**
   * Restore stack to the level prior to the current one. The various Iterator
   * methods will thus return the data at the previous level.
   */
  public void pop() {
    if (depth <= 0) {
      throw new IllegalStateException("Cannot over-pop the stack.");
    }
    scope[depth] = null;
    added[depth] = null;
    depth--;
  }

  /**
   * Return an Iterable containing all the Namespaces introduced to the
   * current-level's scope.
   *
   * @return A read-only Iterable containing added Namespaces (may be empty);
   * @see #push(Element) for the details on the data order.
   */
  public Iterable<Namespace> addedForward() {
    if (added[depth].length == 0) {
      return EMPTYITER;
    }
    return new NamespaceIterable(added[depth], true);
  }

  /**
   * Get all the Namespaces in-scope at the current level of the stack.
   *
   * @return A read-only Iterator containing added Namespaces (may be empty);
   * @see #push(Element) for the details on the data order.
   */
  @Override
  public @NotNull Iterator<Namespace> iterator() {
    return new ForwardWalker(scope[depth]);
  }

  /**
   * Return a new array instance representing the current scope.
   * Modifying the returned array will not affect this scope.
   *
   * @return a copy of the current scope.
   */
  public Namespace[] getScope() {
    return scope[depth].clone();
  }

  /**
   * Inspect the current scope and return true if the specified namespace is
   * in scope.
   *
   * @param ns The Namespace to check
   * @return true if the current scope contains that Namespace.
   */
  public boolean isInScope(Namespace ns) {
    if (ns == scope[depth][0]) {
      return true;
    }
    final int ip = binarySearch(scope[depth], 1, scope[depth].length, ns);
    if (ip >= 0) {
      // we have the same prefix.
      return ns == scope[depth][ip];
    }
    return false;
  }
}
