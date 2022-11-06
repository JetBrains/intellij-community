/*--

 Copyright (C) 2000-2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom;

import org.jdom.util.IteratorIterable;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Iterator;

/**
 * Traverse all a parent's descendants (all children at any level below
 * the parent - excludes the parent itself).
 *
 * @author Bradley S. Huffman
 * @author Jason Hunter
 * @author Rolf Lear
 */
final class DescendantIterator implements IteratorIterable<Content> {
  /**
   * Needed to be Iterable!
   */
  private final Parent parent;

  /*
   * Note, we use an Array of Object here, even through
   * List<Iterator<Content>> would look neater, etc.
   * Fact is, for 'hamlet', using a list for the stack takes about
   * twice as long as using the Object[] array.
   */
  private Object[] stack = new Object[16];
  private int ssize = 0;

  /**
   * The iterator that supplied to most recent next()
   */
  private Iterator<Content> current;
  /**
   * The iterator going down the tree, null unless next() returned Parent
   */
  private Iterator<Content> descending = null;
  /**
   * The iterator going up the tree, null unless next() returned dead-end
   */
  private Iterator<Content> ascending = null;
  /**
   * what it says...
   */
  private boolean hasnext;

  /**
   * Iterator for the descendants of the supplied object.
   *
   * @param parent document or element whose descendants will be iterated
   */
  DescendantIterator(Parent parent) {
    this.parent = parent;
    // can trust that parent is not null, DescendantIterator is package-private.
    current = parent.getContent().iterator();
    hasnext = current.hasNext();
  }

  @Override
  public @NotNull DescendantIterator iterator() {
    // Implement the Iterable stuff.
    return new DescendantIterator(parent);
  }

  /**
   * Returns <b>true</b> if the iteration has more {@link Content} descendants.
   *
   * @return true is the iterator has more descendants
   */
  @Override
  public boolean hasNext() {
    return hasnext;
  }

  /**
   * Returns the next {@link Content} descendant.
   *
   * @return the next descendant
   */
  @Override
  public Content next() {
    // set the 'current' if it needs changing.
    if (descending != null) {
      current = descending;
      descending = null;
    }
    else if (ascending != null) {
      current = ascending;
      ascending = null;
    }

    final Content ret = current.next();

    // got an item to return.
    // sort out the next state....
    if ((ret instanceof Element) && ((Element)ret).getContentSize() > 0) {
      // there is another descendant, and it has values.
      // our next will be down....
      descending = ((Element)ret).getContent().iterator();
      if (ssize >= stack.length) {
        stack = Arrays.copyOf(stack, ssize + 16);
      }
      stack[ssize++] = current;
      return ret;
    }

    if (current.hasNext()) {
      // our next will be along....
      return ret;
    }

    // our next will be up.
    while (ssize > 0) {

      // if the stack was generic, this would not be needed, but,
      // the java.uti.* stack codes are too slow.
      @SuppressWarnings("unchecked") final Iterator<Content> subit = (Iterator<Content>)stack[--ssize];
      ascending = subit;
      stack[ssize] = null;
      if (ascending.hasNext()) {
        return ret;
      }
    }

    ascending = null;
    hasnext = false;
    return ret;
  }

  /**
   * Detaches the last {@link Content} returned by the last call to
   * next from it's parent.  <b>Note</b>: this <b>does not</b> affect
   * iteration and all children, siblings, and any node following the
   * removed node (in document order) will be visited.
   */
  @Override
  public void remove() {
    current.remove();
    // if our next move was to go down, we can't.
    // we can go along, or up.
    descending = null;
    if (current.hasNext() || ascending != null) {
      // we have a next element, or our next move was up anyway.
      return;
    }
    // our next move was going to be down, or accross, but those are not
    // possible any more, need to check up.
    // our next will be up.
    while (ssize > 0) {
      @SuppressWarnings("unchecked") final Iterator<Content> subit = (Iterator<Content>)stack[--ssize];
      stack[ssize] = null;
      ascending = subit;
      if (ascending.hasNext()) {
        return;
      }
    }
    ascending = null;
    hasnext = false;
  }
}
