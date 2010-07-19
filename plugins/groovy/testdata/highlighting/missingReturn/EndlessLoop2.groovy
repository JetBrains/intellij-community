import java.util.concurrent.atomic.AtomicReference

static <S> S apply (AtomicReference<S> self, mutation) {
  for (;;) {
    def s = self.get()
    def newState = mutation(s)
    if (self.compareAndSet(s, newState))
      return newState
  }
}
