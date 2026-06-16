import type { Feature } from '../data'

export default function FeatureItem({ feature }: { feature: Feature }) {
  return (
    <article className="feature-item">
      <span className="feature-dot" aria-hidden="true" />
      <div>
        <h3>{feature.title}</h3>
        <p>{feature.blurb}</p>
      </div>
    </article>
  )
}
