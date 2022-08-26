///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Jason Baldridge All Rights Reserved.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
///////////////////////////////////////////////////////////////////////////////

package gnu.trove;

/**
 * Adapter for TLinkable interface which implements the interface and can
 * therefore be extended trivially to create TLinkable objects without
 * having to implement the obvious.
 *
 * <p>
 * Created: Thurs Nov 15 16:25:00 2001
 * </p>
 *
 * @author Jason Baldridge
 * @version $Id: TLinkableAdaptor.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 * @see TLinkedList
 */

public class TLinkableAdaptor implements TLinkable {
  TLinkable _previous;
  TLinkable _next;

  /**
   * Returns the linked list node after this one.
   *
   * @return a <code>TLinkable</code> value
   */
  @Override
  public TLinkable getNext() {
    return _next;
  }

  /**
   * Returns the linked list node before this one.
   *
   * @return a <code>TLinkable</code> value
   */
  @Override
  public TLinkable getPrevious() {
    return _previous;
  }

  /**
   * Sets the linked list node after this one.
   *
   * @param linkable a <code>TLinkable</code> value
   */
  @Override
  public void setNext(TLinkable linkable) {
    _next = linkable;
  }

  /**
   * Sets the linked list node before this one.
   *
   * @param linkable a <code>TLinkable</code> value
   */
  @Override
  public void setPrevious(TLinkable linkable) {
    _previous = linkable;
  }
}
