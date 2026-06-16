import { FEATURES } from '../data'
import FeatureCard from './FeatureCard'
import FeatureItem from './FeatureItem'

export default function Features() {
  const flagship = FEATURES.filter((f) => f.featured)
  const rest = FEATURES.filter((f) => !f.featured)

  return (
    <section className="section" id="features">
      <div className="section-head">
        <h2>Everything you need to drive agents</h2>
        <p>One launcher, every provider, full IDE context.</p>
      </div>
      <div className="feature-grid">
        {flagship.map((feature) => (
          <FeatureCard key={feature.slug} feature={feature} />
        ))}
      </div>
      <h3 className="subhead">And more</h3>
      <div className="feature-grid-compact">
        {rest.map((feature) => (
          <FeatureItem key={feature.slug} feature={feature} />
        ))}
      </div>
    </section>
  )
}
