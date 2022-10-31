package ml.intellij.nlc.local.tokenizer

// import org.apache.commons.math3.random.MersenneTwister

import org.apache.commons.math3.distribution.UniformRealDistribution
import java.util.*

abstract class BasePriorityQueue<T> {
  abstract fun push(x: T)
  abstract fun pop(): T?
}

class STLQueue<T> : BasePriorityQueue<T>() {
  private val q = PriorityQueue<T>()
  override fun push(x: T) {
    this.q.add(x)
  }

  override fun pop(): T? {
    return this.q.poll()
  }
}


class DropoutQueue<T>(var skipProb: Double) : BasePriorityQueue<T>() {
  //    val rnd = MersenneTwister()
  var dist = UniformRealDistribution(0.0, 1.0)

  private val q = PriorityQueue<T>()
  private val skippedElements = ArrayList<T>()

  override fun push(x: T) {
    q.add(x)
  }

  override fun pop(): T? {
    assert(skippedElements.isEmpty())
    while (true) {
      if (q.isEmpty()) {
        skippedElements.forEach {
          q.add(it)
        }
        skippedElements.clear()
        return null
      }
      val temp = q.peek()
      q.poll()
      if (dist.sample() < skipProb) {
        skippedElements.add(temp)
      }
      else {
        skippedElements.forEach {
          q.add(it)
        }
        skippedElements.clear()
        return temp
      }
    }
  }
}
