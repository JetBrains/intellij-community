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

package org.jdom.xpath.util;

import org.jdom.filter2.Filter;
import org.jdom.xpath.XPathDiagnostic;
import org.jdom.xpath.XPathExpression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A diagnostic implementation useful for diagnosing problems in XPath results.
 * <p>
 * This class tries to make all the data available as part of the internal
 * structure which may assist people who are stepping-through the code from
 * a debugging environment.
 *
 * @param <T> The generic type of the results from the {@link XPathExpression}
 * @author Rolf Lear
 */
final class XPathDiagnosticImpl<T> implements XPathDiagnostic<T> {
  /*
   * Keep nice list references here to help users who debug and step through
   * code. They can inspect the various lists directly.
   */
  private final Object dcontext;
  private final XPathExpression<T> dxpath;
  private final List<Object> draw;
  private final List<Object> dfiltered;
  private final List<T> dresult;
  private final boolean dfirstonly;

  /**
   * Create a useful Diagnostic instance for tracing XPath query results.
   *
   * @param dcontext   The context against which the XPath query was run.
   * @param dxpath     The {@link XPathExpression} instance which created this diagnostic.
   * @param inraw      The data as returned from the XPath library.
   * @param dfirstonly If the XPath library was allowed to terminate after the first result.
   */
  XPathDiagnosticImpl(Object dcontext, XPathExpression<T> dxpath,
                      List<?> inraw, boolean dfirstonly) {

    final int sz = inraw.size();
    final List<Object> raw = new ArrayList<>(sz);
    final List<Object> filtered = new ArrayList<>(sz);
    final List<T> result = new ArrayList<>(sz);
    final Filter<T> filter = dxpath.getFilter();

    for (Object o : inraw) {
      raw.add(o);
      T t = filter.filter(o);
      if (t == null) {
        filtered.add(o);
      }
      else {
        result.add(t);
      }
    }

    this.dcontext = dcontext;
    this.dxpath = dxpath;
    this.dfirstonly = dfirstonly;

    this.dfiltered = Collections.unmodifiableList(filtered);
    this.draw = Collections.unmodifiableList(raw);
    this.dresult = Collections.unmodifiableList(result);
  }

  @Override
  public Object getContext() {
    return dcontext;
  }

  @Override
  public XPathExpression<T> getXPathExpression() {
    return dxpath;
  }

  @Override
  public List<T> getResult() {
    return dresult;
  }

  @Override
  public String toString() {
    return String.format("[XPathDiagnostic: '%s' evaluated (%s) against " +
                         "%s produced  raw=%d discarded=%d returned=%d]",
                         dxpath.getExpression(), (dfirstonly ? "first" : "all"),
                         dcontext.getClass().getName(), draw.size(), dfiltered.size(),
                         dresult.size());
  }
}
