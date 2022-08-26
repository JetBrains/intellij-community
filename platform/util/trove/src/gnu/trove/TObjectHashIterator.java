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

/**
 * Created: Wed Nov 28 21:30:53 2001
 *
 * @author Eric D. Friedman
 * @version $Id: TObjectHashIterator.java,v 1.8 2004/09/24 09:11:15 cdr Exp $
 */

final class TObjectHashIterator<E> extends THashIterator<E> {
  private final TObjectHash<E> _objectHash;

  TObjectHashIterator(TObjectHash<E> hash) {
    super(hash);
    _objectHash = hash;
  }

  @Override
  protected E objectAtIndex(int index) {
    return (E)_objectHash._set[index];
  }
} // TObjectHashIterator
