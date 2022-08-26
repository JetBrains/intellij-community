///////////////////////////////////////////////////////////////////////////////
// Copyright (c) 2001, Eric D. Friedman All Rights Reserved.
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

import java.io.Serializable;


/**
 * Interface for Objects which can be inserted into a TLinkedList.
 *
 * <p>
 * Created: Sat Nov 10 15:23:41 2001
 * </p>
 *
 * @author Eric D. Friedman
 * @version $Id: TLinkable.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 * @see TLinkedList
 */

public interface TLinkable extends Serializable {

  /**
   * Returns the linked list node after this one.
   *
   * @return a <code>TLinkable</code> value
   */
  TLinkable getNext();

  /**
   * Returns the linked list node before this one.
   *
   * @return a <code>TLinkable</code> value
   */
  TLinkable getPrevious();

  /**
   * Sets the linked list node after this one.
   *
   * @param linkable a <code>TLinkable</code> value
   */
  void setNext(TLinkable linkable);

  /**
   * Sets the linked list node before this one.
   *
   * @param linkable a <code>TLinkable</code> value
   */
  void setPrevious(TLinkable linkable);
}// TLinkable
