/*--

 Copyright (C) 2012 Jason Hunter & Brett McLaughlin.
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

package org.jdom.output;

import org.jdom.Content;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * This Walker implementation walks a list of Content in its original RAW
 * format. There is no text manipulation, and all content will be returned as
 * the input type. In other words, next() will never be null, and text() will
 * always be null.
 *
 * @author Rolf Lear
 */
final class WalkerPRESERVE implements Walker {
  private static final Iterator<Content> EMPTYIT = new Iterator<Content>() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Content next() {
      throw new NoSuchElementException("Cannot call next() on an empty iterator.");
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("Cannot remove from an empty iterator.");
    }
  };

  private final Iterator<? extends Content> iter;
  private final boolean alltext;

  /**
   * Create a Walker that preserves all content in its raw state.
   *
   * @param content the content to walk.
   */
  WalkerPRESERVE(final List<? extends Content> content) {
    super();
    if (content.isEmpty()) {
      alltext = true;
      iter = EMPTYIT;
    }
    else {
      iter = content.iterator();
      alltext = false;
    }
  }

  @Override
  public boolean isAllText() {
    return alltext;
  }

  @Override
  public boolean hasNext() {
    return iter.hasNext();
  }

  @Override
  public Content next() {
    return iter.next();
  }

  @Override
  public String text() {
    return null;
  }

  @Override
  public boolean isCDATA() {
    return false;
  }
}
