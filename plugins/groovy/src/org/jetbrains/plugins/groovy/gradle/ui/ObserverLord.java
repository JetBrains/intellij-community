/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.gradle.ui;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.awt.EventQueue;

/**<!=========================================================================>

   This is a Swing-friendly observer manager class. Swing-friendly, but can be
   used by non-Swing classes. Its meant to abstract the fact that you probably
   need to be in the Event Dispatch Thread when receiving notifications inside
   Swing-related classes.

     To use this class, add it as a member variable (don't derive from this!)
   of a class that you want to be observered. You can have multiple instances
   of this if you want to allow for a finer granularity of observing (similar
   to components having mouse move listeners and mouse (click) listeners).
   Next, create an interface for the observers. Now implement add and remove
   observer functions that call the add and remove functions here. Lastly,
   implement ObserverNotification and have it call the aforementioned observer
   interface appropriately. Note: you should actually implement ObserverNotification
   for each "message" you want to send. Example: One that would tell a view
   a node was added. One that would tell a view a node was deleted, etc.
   While you have multiple notification classes, you only need 1 (or few)
   actual observer interfaces, containing all the possible functions called
   by all notifications.
   @author mhunsicker
<!==========================================================================>*/

public class ObserverLord<E>
{
   ////////////////////////////////////////////////////////////////////////////
   //////////////////////////// member variables //////////////////////////////
   ////////////////////////////////////////////////////////////////////////////
   private final List<E> regularObservers = new ArrayList<E>();
   private final List<E> eventQueueObservers = new ArrayList<E>();


   //
         /**<!=========================================================================>
          Implement this for each call to ObserverLord.notifyObservers. The notify
          function usually just has a single call to a function on the observer.

          Example:

             public void notify( MyObserver observer )
             {
                observer.myfunction();
             }

          @author mhunsicker
          <!==========================================================================>*/
         public interface ObserverNotification<E>
         {
            public void notify( E observer );
         }


   ////////////////////////////////////////////////////////////////////////////
   ///////////////////////////////// methods //////////////////////////////////
   ////////////////////////////////////////////////////////////////////////////

   /**<!===== addObserver =============================================>
      Adds an observer to our messaging system.

      <!       Name        Description  >
      @param   observer    observer to add.
      @param   inEventQueue true to notify this observer only in the event queue,
                            false to notify it immediately.
      @author  mhunsicker
   <!=======================================================================>*/
   public void addObserver( E observer, boolean inEventQueue )
   {
		if( !inEventQueue )
         addIfNew( observer, regularObservers );
		else
         addIfNew( observer, eventQueueObservers  );
   }

   private void addIfNew( E observer, List<E> destinationList )
   {
      if( !destinationList.contains( observer ) )
         destinationList.add( observer );
   }

   /**<!===== removeObserver ==========================================>
      Deletes an observer in our messaging system.

      <!       Name     Dir   Description  >
      @param   observer in,

      @author  mhunsicker
   <!=======================================================================>*/
   public void removeObserver( E observer )
   {
      regularObservers.remove( observer );
      eventQueueObservers.remove( observer );
   }


   public void removeAllObservers()
   {
      regularObservers.clear();
      eventQueueObservers.clear();
   }

   /**<!===== notifyObservers ================================================>
      Messaging method that handles telling each observer that something
      happen to the observable.

      <!       Name        Dir   Description  >
      @param   notification in,  notification sent to the observer

      @author  mhunsicker
   <!=======================================================================>*/
   public void notifyObservers( ObserverNotification<E> notification )
   {
      //notify all the non-event queue observers now.
      notifyObserversInternal( regularObservers, notification );
      notifyObserversInEventQueueThread( notification );
   }

   /**<!===== notifyObserversInEventQueueThread ====================================>
      Here is where we notify all the event queue observers. To notify the event
      queue observers we have to make sure it occurs in the event queue thread. If
      we're not in the event queue, we'll wrap it in an invoke and wait.

      <!       Name        Dir   Description  >
      <!       Name        Dir   Description  >
      @param   notification in,  notification sent to the observer

      @author  mhunsicker
   <!=======================================================================>*/
   private void notifyObserversInEventQueueThread( final ObserverNotification notification )
   {
      if( eventQueueObservers.size() == 0 ) //if we have no event queue observsers, we're done
         return;

      if( EventQueue.isDispatchThread() )
         notifyObserversInternal( eventQueueObservers, notification );
      else
      {
         try
         {
            SwingUtilities.invokeAndWait( new Runnable()
            {
               public void run()
               {
                  notifyObserversInternal( eventQueueObservers, notification );
               }
            } );
         }
         catch( Exception e )
         {
            //System.out.println( "notifyObservers exception: " + e.toString() );
            e.printStackTrace();
         }
      }
   }

   /**<!===== notifyObserversInternal ========================================>
      The internal mechanism that actually notifies the observers. We just
      iterate though each observer and pass it to the notification mechanism.


      <!       Name         Dir  Description  >
      @param   observers    in,  objects that changed (observable)
      @param   notification in,  notification sent to the observer

      @author  mhunsicker
   <!=======================================================================>*/
	private void notifyObserversInternal( List<E> observers, ObserverNotification notification )
   {
      Iterator<E> iterator = observers.iterator();
      while( iterator.hasNext() )
      {
         E observer = iterator.next();
         try
         {
            notification.notify( observer );
         }
         catch( Exception e ) //this is so an error in the notification doesn't stop the entire process.
         {
            e.printStackTrace();
         }
      }
   }

   public String toString()
   {
      return regularObservers.size() + " regular observers, " + eventQueueObservers.size() + " event queue observers";
   }
}
