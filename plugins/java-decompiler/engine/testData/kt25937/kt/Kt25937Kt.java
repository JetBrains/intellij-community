package kt;

import kotlin.Metadata;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

@Metadata(
   mv = {1, 1, 16},
   bv = {1, 0, 3},
   k = 2,
   d1 = {"\u0000\u001c\n\u0000\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\u0002\n\u0002\u0010\u0000\n\u0002\b\u0003\u001a,\u0010\u0000\u001a\u00020\u00012\u001c\u0010\u0002\u001a\u0018\b\u0001\u0012\n\u0012\b\u0012\u0004\u0012\u00020\u00050\u0004\u0012\u0006\u0012\u0004\u0018\u00010\u00060\u0003ø\u0001\u0000¢\u0006\u0002\u0010\u0007\u001a\u0006\u0010\b\u001a\u00020\u0001\u0082\u0002\u0004\n\u0002\b\u0019¨\u0006\t"},
   d2 = {"callSuspendBlock", "", "block", "Lkotlin/Function1;", "Lkotlin/coroutines/Continuation;", "", "", "(Lkotlin/jvm/functions/Function1;)I", "callSuspendBlockGood", "kotlinx-test"}
)
public final class Kt25937Kt {
   public static final int callSuspendBlock(@NotNull Function1<? super Continuation<? super Unit>, ? extends Object> block) {
      Intrinsics.checkParameterIsNotNull(block, "block");
      return 1;
   }

   public static final int callSuspendBlockGood() {
      return 1;
   }
}
