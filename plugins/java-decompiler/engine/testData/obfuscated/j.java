class j extends i<ak> {
   final ai a;

   j(ai var1) {
      this.a = var1;
   }

   public void a(ak var1) {
      al var2 = (al)ai.b().get(var1);

      al var10000;
      label21: {
         label20: {
            try {
               var10000 = var2;
               if (an.k) {
                  break label21;
               }

               if (var2 != null) {
                  break label20;
               }
            } catch (a_ var3) {
               throw var3;
            }

            var2 = new al(var1);
            ai.b().put(var1, var2);
         }

         var10000 = var2;
      }

      var10000.a(var1.d());
   }
}
