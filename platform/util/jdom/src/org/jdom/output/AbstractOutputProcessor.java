/*-- 

 Copyright (C) 2011-2012 Jason Hunter & Brett McLaughlin.
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

import java.util.List;

/**
 * Methods common/useful for all Outputter processors.
 *
 * @author Rolf Lear
 * @since JDOM2
 */
abstract class AbstractOutputProcessor {
  /**
   * Create a walker to process Content List values.
   * <p>
   * If you require a custom walker to process content in a specific way
   * then you probably want to override this method to build the walker you
   * want.
   *
   * @param formatStack The current FormatStack for the walker (this should not be
   *                    modified by the Walker).
   * @param content     The list of content to walk.
   * @param escape      If you want the Text values to be XMLEscaped then supply
   *                    a non-null EscapeStrategy to use.
   * @return the created walker.
   */
  Walker buildWalker(FormatStack formatStack, List<? extends Content> content, boolean escape) {
    switch (formatStack.getTextMode()) {
      case PRESERVE:
        return new WalkerPRESERVE(content);
      case NORMALIZE:
        return new WalkerNORMALIZE(content, formatStack, escape);
      case TRIM:
        return new WalkerTRIM(content, formatStack, escape);
      case TRIM_FULL_WHITE:
        return new WalkerTRIM_FULL_WHITE(content, formatStack, escape);
    }
    // all cases should be handled in the switch statement above. If someone
    // creates a new TextMode though, then it will create a warning in
    // eclipse above, and the code will fall through to this 'default' raw
    // instance.
    return new WalkerPRESERVE(content);
  }
}
